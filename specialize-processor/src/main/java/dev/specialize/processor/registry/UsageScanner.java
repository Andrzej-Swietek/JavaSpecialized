package dev.specialize.processor.registry;

import dev.specialize.processor.resolve.NameResolver;

import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;

/** {@code @Specialize(autoscan = true)}: records the primitive type arguments a compilation uses a template with. */
public final class UsageScanner extends TreeScanner {
    private final SpecRegistry registry;
    private final NameResolver resolver;

    public UsageScanner(SpecRegistry registry, NameResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
    }

    private void usage(JCExpression clazz, List<JCExpression> typeArguments) {
        resolver.resolveTypeName(clazz).ifPresent(name -> registry.autoscanned(name).ifPresent(template ->
                template.tupleFor(typeArguments, resolver, registry.isAlias(name)).ifPresent(tuple -> registry.addScannedType(template, tuple))));
    }

    @Override
    public void visitTypeApply(JCTypeApply tree) {
        super.visitTypeApply(tree);
        usage(tree.clazz, tree.arguments);
    }

    @Override
    public void visitApply(JCMethodInvocation tree) {
        super.visitApply(tree);
        if (tree.meth instanceof JCFieldAccess access && !tree.typeargs.isEmpty()) {
            usage(access.selected, tree.typeargs);
        }
    }
}
