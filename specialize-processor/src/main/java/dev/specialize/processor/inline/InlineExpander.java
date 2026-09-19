package dev.specialize.processor.inline;

import dev.specialize.processor.resolve.ClassScope;
import dev.specialize.processor.resolve.NameResolver;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Names;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Replaces calls to {@code @Inline} methods by their bodies, recursively; past {@link #MAX_DEPTH} nested expansions a call is kept.
 */
public final class InlineExpander extends TreeTranslator {
    private static final int MAX_DEPTH = 16;

    private final TreeMaker make;
    private final Names names;
    private final InlineRegistry inlines;
    private final NameResolver resolver;
    private final ClassScope scope;
    private final LocalNames locals;
    private int depth;

    public InlineExpander(
            TreeMaker make,
            Names names,
            InlineRegistry inlines,
            NameResolver resolver,
            ClassScope scope
    ) {
        this.make = make;
        this.names = names;
        this.inlines = inlines;
        this.resolver = resolver;
        this.scope = scope;
        this.locals = new LocalNames();
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        scope.enter(tree.name.toString());
        super.visitClassDef(tree);
        scope.leave();
    }

    /**
     * A statement calling a {@code void} body becomes the body as a statement; a non-void block body stays a call (a switch expression is not a statement).
     */
    @Override
    public void visitExec(JCExpressionStatement statement) {
        Optional<JCMethodInvocation> call = statement.expr instanceof JCMethodInvocation c && c.typeargs.isEmpty() ? Optional.of(c) : Optional.empty();
        Optional<InlineMethod> inline = call.flatMap(this::resolve).filter(m -> m.isVoid() || m.isBlock());
        if (inline.isEmpty()) {
            super.visitExec(statement);
            return;
        }
        JCMethodInvocation c = call.orElseThrow();
        c.args = translate(c.args);
        result = inline.filter(InlineMethod::isVoid)
                .flatMap(m -> m.expandStatement(c.args, make.at(c.pos), names, locals))
                .<JCTree>map(this::expandNested)
                .orElse(statement);
    }

    @Override
    public void visitApply(JCMethodInvocation call) {
        super.visitApply(call);
        result = call.typeargs.isEmpty()
                ? resolve(call)
                .flatMap(inline -> inline.expand(call.args, make.at(call.pos), names, locals))
                .map(this::expandNested)
                .orElse(call)
                : call;
    }

    private <T extends JCTree> T expandNested(T expanded) {
        if (depth >= MAX_DEPTH) {
            return expanded;
        }
        depth++;
        try {
            return translate(expanded);
        } finally {
            depth--;
        }
    }

    private Optional<InlineMethod> resolve(JCMethodInvocation call) {
        int arity = call.args.size();
        if (call.meth instanceof JCFieldAccess access) {
            return resolver.resolveTypeName(access.selected)
                    .flatMap(owner -> inlines.find(owner, access.name.toString(), arity));
        }
        String name = ((JCIdent) call.meth).name.toString(); // javac: meth is an Ident or a Select
        Stream<String> owners = Stream.concat(
                scope.enclosingClasses().stream(),
                Stream.concat(
                        resolver.staticImportOwner(name).stream(),
                        resolver.staticOnDemandOwners().stream()
                )
        );
        return owners.map(owner -> inlines.find(owner, name, arity)).flatMap(Optional::stream).findFirst();
    }
}
