package dev.specialize.processor.rewrite;

import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMemberReference;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCSwitchExpression;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCYield;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Points an expression that must produce a specialization at that class: {@code Opt.some(v)} → {@code OptInt.some(v)},
 * {@code some(v)} (static import) → {@code OptInt.some(v)}, {@code new Opt<>(v)} → {@code new OptInt(v)},
 * {@code new Opt[n]} → {@code new OptInt[n]}, looking through parentheses, casts, conditionals, switch expressions,
 * the receiver of a method chain ({@code Opt.empty().filter(p)}), lambda bodies and method references.
 */
public final class Retargeter {
    private final TreeMaker make;
    private final Predicate<JCTree> namesTemplate;
    private final Predicate<Name> staticallyImportedFromTemplate;
    private final Supplier<JCExpression> replacement;
    private final boolean keepsTypeParameters;

    /** {@code keepsTypeParameters}: a generic template keeps its diamond, {@code new Map2<>} → {@code new Map2Int<>}. */
    public Retargeter(TreeMaker make, Predicate<JCTree> namesTemplate, Predicate<Name> staticallyImportedFromTemplate,
               Supplier<JCExpression> replacement, boolean keepsTypeParameters) {
        this.make = make;
        this.namesTemplate = namesTemplate;
        this.staticallyImportedFromTemplate = staticallyImportedFromTemplate;
        this.replacement = replacement;
        this.keepsTypeParameters = keepsTypeParameters;
    }

    /** Rewrites {@code expression} in place and returns it; {@code null} (a valueless {@code return}) passes through. */
    public JCExpression retarget(JCExpression expression) {
        switch (expression) {
            case null -> { }
            case JCParens p -> p.expr = retarget(p.expr);
            case JCConditional c -> {
                c.truepart = retarget(c.truepart);
                c.falsepart = retarget(c.falsepart);
            }
            case JCSwitchExpression s -> s.cases.forEach(this::retargetCase);
            case JCLambda lambda when lambda.body instanceof JCExpression value -> lambda.body = retarget(value);
            case JCLambda lambda -> retargetReturns(lambda.body);
            case JCMemberReference ref when namesTemplate.test(ref.expr) -> ref.expr = replace(ref.pos);
            case JCTypeCast cast -> cast.expr = retarget(cast.expr);
            case JCMethodInvocation call when call.typeargs.isEmpty() -> retargetCall(call);
            case JCNewClass creation when isDiamondTemplate(creation) -> creation.clazz = diamond(creation.pos);
            case JCNewClass creation when namesTemplate.test(creation.clazz) -> creation.clazz = replace(creation.pos);
            case JCNewArray array -> retargetArray(array);
            default -> { }
        }
        return expression;
    }

    /** {@code new Opt[n]} → {@code new OptInt[n]}; the elements of a {@code {a, b}} initializer are targets themselves. */
    private void retargetArray(JCNewArray array) {
        if (namesTemplate.test(array.elemtype)) {
            array.elemtype = replace(array.pos);
        }
        if (array.elems != null) {
            array.elems = array.elems.map(this::retarget);
        }
    }

    private void retargetCall(JCMethodInvocation call) {
        switch (call.meth) {
            case JCFieldAccess access when namesTemplate.test(access.selected) -> access.selected = replace(access.pos);
            case JCFieldAccess access when access.selected instanceof JCMethodInvocation || access.selected instanceof JCParens ->
                    access.selected = retarget(access.selected);
            case JCIdent name when staticallyImportedFromTemplate.test(name.name) ->
                    call.meth = make.at(name.pos).Select(replacement.get(), name.name);
            default -> { }
        }
    }

    private void retargetCase(JCCase c) {
        if (c.body instanceof JCExpression value) {
            c.body = retarget(value);
        } else {
            new ExitScanner() {
                @Override
                public void visitYield(JCYield tree) {
                    tree.value = retarget(tree.value);
                }
            }.scan(c.stats);
        }
    }

    private void retargetReturns(JCTree lambdaBody) {
        new ExitScanner() {
            @Override
            public void visitReturn(JCReturn tree) {
                tree.expr = retarget(tree.expr);
            }
        }.scan(lambdaBody);
    }

    /** Visits the exits of one body without entering nested bodies that have targets of their own. */
    private abstract static class ExitScanner extends TreeScanner {
        @Override
        public void visitSwitchExpression(JCSwitchExpression nested) {
        }

        @Override
        public void visitLambda(JCLambda nested) {
        }

        @Override
        public void visitClassDef(JCClassDecl nested) {
        }
    }

    private boolean isDiamondTemplate(JCNewClass creation) {
        return TreeUtil.asTypeApply(creation.clazz).filter(ta -> ta.arguments.isEmpty() && namesTemplate.test(ta.clazz)).isPresent();
    }

    private JCExpression replace(int pos) {
        make.at(pos);
        return replacement.get();
    }

    private JCExpression diamond(int pos) {
        JCExpression replaced = replace(pos);
        return keepsTypeParameters ? make.TypeApply(replaced, List.nil()) : replaced;
    }
}
