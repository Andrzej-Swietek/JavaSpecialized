package dev.specialize.processor.inline;

import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Replaces the free names of an inline body by fully qualified ones, so it means the same at every call site.
 * {@code bound}: parameters, locals and type variables, left alone; {@code publicStaticMembers}: the owner's statics by name.
 */
final class Qualifier extends TreeTranslator {
    private final TreeMaker make;
    private final Names names;
    private final String owner;
    private final Set<Name> bound;
    private final Map<Name, Boolean> publicStaticMembers;
    private final NameResolver resolver;
    private Optional<String> problem = Optional.empty();

    Qualifier(TreeMaker make, Names names, String owner, Set<Name> bound, Map<Name, Boolean> publicStaticMembers, NameResolver resolver) {
        this.make = make;
        this.names = names;
        this.owner = owner;
        this.bound = bound;
        this.publicStaticMembers = publicStaticMembers;
        this.resolver = resolver;
    }

    /** The first name that could not be qualified, if any. */
    Optional<String> problem() {
        return problem;
    }

    @Override
    public void visitIdent(JCIdent ident) {
        make.at(ident.pos);
        if (bound.contains(ident.name)) {
            result = ident;
        } else if (publicStaticMembers.containsKey(ident.name)) {
            if (!publicStaticMembers.get(ident.name)) {
                report("references non-public member '" + ident.name + "' which call sites in other packages cannot access");
            }
            result = make.Select(TreeUtil.qualIdent(make, names, owner), ident.name);
        } else {
            Optional<JCExpression> qualified = resolver.resolveTypeName(ident).<JCExpression>map(q -> TreeUtil.qualIdent(make, names, q))
                    .or(() -> resolver.staticImportOwner(ident.name.toString())
                            .map(imported -> make.Select(TreeUtil.qualIdent(make, names, imported), ident.name)));
            result = qualified.orElseGet(() -> {
                report("references '" + ident.name + "' which is neither a parameter, a type variable, a static member of "
                        + owner + " nor a resolvable type");
                return ident;
            });
        }
    }

    @Override
    public void visitSelect(JCFieldAccess access) {
        access.selected = translate(access.selected);
        result = access;
    }

    private void report(String message) {
        problem = problem.or(() -> Optional.of(message));
    }
}
