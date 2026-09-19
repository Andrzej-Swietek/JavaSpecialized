package dev.specialize.processor.inline;

import dev.specialize.processor.resolve.Match;

import com.sun.source.tree.CaseTree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCaseLabel;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.HashMap;
import java.util.Map;

/** What inlining a block needs: its locals under fresh names, and its statements as a {@code switch (0) { default -> … }} expression. */
final class InlinedBlocks {
    private InlinedBlocks() {
    }

    /** The block's locals mapped to {@code name + tag + group}. */
    static Map<Name, Name> renamedLocals(JCTree body, Names names, String tag, int group) {
        Map<Name, Name> renamed = new HashMap<>();
        new TreeScanner() {
            @Override
            public void visitVarDef(JCVariableDecl local) {
                renamed.put(local.name, names.fromString(local.name + tag + group));
                super.visitVarDef(local);
            }
        }.scan(body);
        return renamed;
    }

    /** Applies {@code substitution} to identifiers and {@code renamed} to declarations in {@code tree}. */
    static <T extends JCTree> T substituted(T tree, Map<Name, JCExpression> substitution, Map<Name, Name> renamed, TreeMaker make) {
        return new TreeTranslator() {
            @Override
            public void visitIdent(JCIdent ident) {
                make.at(ident.pos);
                result = java.util.Optional.ofNullable(substitution.get(ident.name)).<JCTree>map(bound -> new com.sun.tools.javac.tree.TreeCopier<Void>(make).copy(bound))
                        .or(() -> java.util.Optional.ofNullable(renamed.get(ident.name)).map(make::Ident))
                        .orElse(ident);
            }

            @Override
            public void visitVarDef(JCVariableDecl local) {
                super.visitVarDef(local);
                local.name = renamed.getOrDefault(local.name, local.name);
                result = local;
            }
        }.translate(tree);
    }

    /** {@code { stmts; return e; }} → {@code switch (0) { default -> { stmts; yield e; } }}; returns of nested lambdas and classes stay. */
    static JCExpression switchExpression(List<JCStatement> stats, TreeMaker make) {
        JCBlock yielding = new OwnScope() {
            @Override
            public void visitReturn(JCReturn tree) {
                result = make.at(tree.pos).Yield(tree.expr);
            }
        }.translate(make.Block(0, stats));
        List<JCCaseLabel> labels = List.of(make.DefaultCaseLabel());
        return make.SwitchExpression(make.Literal(0), List.of(make.Case(CaseTree.CaseKind.RULE, labels, null, List.of(yielding), yielding)));
    }

    /** Whether a {@code return} occurs in the body itself, outside nested lambdas and classes. */
    static boolean returns(JCTree body) {
        return Match.in(body, new OwnScopeMatch() {
            @Override
            public void visitReturn(JCReturn tree) {
                found = true;
            }
        });
    }

    /** Translates one body without entering nested lambdas or classes, whose returns are their own. */
    abstract static class OwnScope extends TreeTranslator {
        @Override
        public void visitLambda(JCLambda nested) {
            result = nested;
        }

        @Override
        public void visitClassDef(JCClassDecl nested) {
            result = nested;
        }
    }

    abstract static class OwnScopeMatch extends Match {
        @Override
        public void visitLambda(JCLambda nested) {
        }

        @Override
        public void visitClassDef(JCClassDecl nested) {
        }
    }
}
