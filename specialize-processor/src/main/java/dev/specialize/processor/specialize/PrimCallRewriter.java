package dev.specialize.processor.specialize;

import dev.specialize.processor.model.PrimitiveTarget;
import dev.specialize.processor.model.ReferenceTarget;
import dev.specialize.processor.model.TargetType;

import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code Prim.eq(a, b)} → {@code a == b} once the primitive the values are of is known. */
record PrimCallRewriter(TreeMaker make, Names names, Substitution substitution) {

    /** {@code explicitTarget}: the {@code K} of {@code Prim.<K>zero()}; {@code declared}: the declared target of names in scope. */
    Optional<JCExpression> rewrite(JCMethodInvocation call, Optional<TargetType> explicitTarget, Map<Name, Optional<TargetType>> declared) {
        return PrimHelper.of(call.meth).flatMap(helper -> explicitTarget.or(() -> inferredTarget(helper.valueArguments(call.args), declared))
                .flatMap(target -> switch (target) {
                    case PrimitiveTarget p -> Optional.of(helper.rewrite(new PrimHelper.Ctx(make, names, p), call.args));
                    case ReferenceTarget r -> helper.rewriteReference(make, names, r, call.args);
                }));
    }

    /**
     * The single target the value arguments are declared with: {@code Prim.eq(name, o.name)} on a {@code String} field
     * stays {@code Objects.equals}; with one specialized variable, undeclared values are assumed to be it.
     */
    private Optional<TargetType> inferredTarget(List<JCExpression> values, Map<Name, Optional<TargetType>> declared) {
        java.util.List<Optional<TargetType>> known = values.stream()
                .map(PrimCallRewriter::variableName).flatMap(Optional::stream)
                .map(declared::get).filter(Objects::nonNull)
                .toList();
        if (known.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        Set<TargetType> targets = known.stream().flatMap(Optional::stream).collect(Collectors.toSet());
        return targets.size() == 1 ? targets.stream().findFirst() : substitution.single();
    }

    /** {@code x}, {@code this.x} or {@code other.x} as a name whose declared type may be known. */
    private static Optional<Name> variableName(JCExpression expression) {
        return switch (expression) {
            case JCIdent id -> Optional.of(id.name);
            case JCFieldAccess access when access.selected instanceof JCIdent -> Optional.of(access.name);
            default -> Optional.empty();
        };
    }
}
