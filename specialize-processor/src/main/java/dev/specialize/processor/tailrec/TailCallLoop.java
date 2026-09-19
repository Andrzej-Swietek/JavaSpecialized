package dev.specialize.processor.tailrec;

import dev.specialize.processor.Diagnostics;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCContinue;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCLabeledStatement;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCThrow;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Optional;
import java.util.stream.IntStream;

/** One {@code @TailRec} method turned into {@code while (true)}: tail calls assign the parameters and {@code continue}. */
final class TailCallLoop {
    private static final String TEMP_SUFFIX = "$tail";

    private final TreeMaker make;
    private final Names names;
    private final Diagnostics diagnostics;
    private final JCClassDecl enclosing;
    private final JCMethodDecl method;
    private final boolean isVoid;
    private int rewritten;

    TailCallLoop(TreeMaker make, Names names, Diagnostics diagnostics, JCClassDecl enclosing, JCMethodDecl method) {
        this.make = make;
        this.names = names;
        this.diagnostics = diagnostics;
        this.enclosing = enclosing;
        this.method = method;
        this.isVoid = TreeUtil.isVoid(method.restype);
    }

    void apply() {
        List<JCStatement> body = tailStatements(method.body.stats);
        Optional<JCMethodInvocation> stray = firstRemainingSelfCall(body);
        if (stray.isPresent()) {
        diagnostics.error(stray.get(), "@TailRec " + method.name + ": recursive call not in tail position");
        return;
        }
        if (rewritten == 0) {
        diagnostics.error(method, "@TailRec " + method.name + ": no tail call to itself found; the method is left unchanged. Remove @TailRec or write the recursive call as return f(..)");
        return;
        }
        if (isVoid && !completesAbruptly(body.last())) {
        body = body.append(make.Return(null)); // falling off the end must leave the loop, not iterate again
        }
        make.at(method.body.pos);
        method.body = make.Block(0, List.of(make.WhileLoop(make.Literal(TypeTag.BOOLEAN, 1), make.Block(0, body))));
    }


    private List<JCStatement> tailStatements(List<JCStatement> statements) {
        if (statements.isEmpty()) {
        return statements;
        }
        List<JCStatement> init = List.from(statements.stream().limit(statements.size() - 1).toList());
        return init.append(tailStatement(statements.last()));
    }

    private JCStatement tailStatement(JCStatement statement) {
        return switch (statement) {
        case JCReturn ret when ret.expr != null -> tailReturn(ret);
        case JCExpressionStatement exec when isVoid && exec.expr instanceof JCMethodInvocation call && isSelfCall(call) -> loopBack(call);
        case JCBlock block -> {
            block.stats = tailStatements(block.stats);
            yield block;
        }
        case JCIf branch -> {
            branch.thenpart = tailStatement(branch.thenpart);
            if (branch.elsepart != null) {
            branch.elsepart = tailStatement(branch.elsepart);
            }
            yield branch;
        }
        case JCLabeledStatement labeled -> {
            labeled.body = tailStatement(labeled.body);
            yield labeled;
        }
        case JCSwitch sw -> {
            sw.cases.forEach(this::tailCase);
            yield sw;
        }
        default -> statement;
        };
    }

    private void tailCase(JCCase c) {
        c.stats = tailStatements(c.stats);
        if (c.body instanceof JCStatement body) {
        c.body = tailStatement(body);
        }
    }

    private JCStatement tailReturn(JCReturn ret) {
        JCExpression value = TreeUtil.unparenthesized(ret.expr);
        if (value instanceof JCConditional cond) {
        make.at(ret.pos);
        JCStatement then = tailReturn(make.Return(cond.truepart));
        JCStatement otherwise = tailReturn(make.Return(cond.falsepart));
        return make.If(cond.cond, then, otherwise);
        }
        return value instanceof JCMethodInvocation call && isSelfCall(call) ? loopBack(call) : ret;
    }

    /** {@code return f(e1, e2);} → {@code { T1 t1 = e1; T2 t2 = e2; p1 = t1; p2 = t2; continue; }}. */
    private JCStatement loopBack(JCMethodInvocation call) {
        rewritten++;
        make.at(call.pos);
        TreeCopier<Void> copier = new TreeCopier<>(make);
        List<JCStatement> temporaries = IntStream.range(0, method.params.size())
            .<JCStatement>mapToObj(i -> make.VarDef(make.Modifiers(0), tempName(i), copier.copy(method.params.get(i).vartype), call.args.get(i)))
            .collect(List.collector());
        List<JCStatement> assignments = IntStream.range(0, method.params.size())
            .<JCStatement>mapToObj(i -> make.Exec(make.Assign(make.Ident(method.params.get(i).name), make.Ident(tempName(i)))))
            .collect(List.collector());
        return make.Block(0, temporaries.appendList(assignments).append(make.Continue(null)));
    }

    private Name tempName(int i) {
        return names.fromString(method.params.get(i).name + TEMP_SUFFIX + i);
    }


    /** {@code f(..)}, {@code this.f(..)} or {@code Owner.f(..)} with this method's name and arity. */
    private boolean isSelfCall(JCMethodInvocation call) {
        return call.args.size() == method.params.size() && switch (call.meth) {
        case JCIdent id -> id.name == method.name;
        case JCFieldAccess access when access.name == method.name && access.selected instanceof JCIdent qualifier ->
            qualifier.name == names._this || qualifier.name == enclosing.name;
        default -> false;
        };
    }

    /** The first self call left anywhere in the body, lambda bodies included. */
    private Optional<JCMethodInvocation> firstRemainingSelfCall(List<JCStatement> body) {
        JCMethodInvocation[] found = {null};
        new TreeScanner() {
        @Override
        public void visitApply(JCMethodInvocation call) {
            if (found[0] == null && isSelfCall(call)) {
            found[0] = call;
            }
            super.visitApply(call);
        }
        }.scan(body);
        return Optional.ofNullable(found[0]);
    }

    /** {@code try} and {@code synchronized} never count as abrupt: their bodies are not tail positions. */
    private boolean completesAbruptly(JCStatement statement) {
        return switch (statement) {
        case JCReturn _ -> true;
        case JCThrow _ -> true;
        case JCContinue _ -> true;
        case JCBlock block -> block.stats.nonEmpty() && completesAbruptly(block.stats.last());
        case JCIf branch -> branch.elsepart != null && completesAbruptly(branch.thenpart) && completesAbruptly(branch.elsepart);
        default -> false;
        };
    }
    
}
