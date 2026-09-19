package dev.specialize.processor.specialize;

import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.specialize.FunctionalInterfaces.Mapping;

import com.sun.tools.javac.util.Name;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** What is visible while a template is translated: the mapped and declared names, and the shadowing in effect. */
final class TranslationScope {
    /** Names visible in the method being translated: those mapped to a primitive functional interface, and the declared target of each. */
    private record MethodScope(Map<Name, Mapping> mappedVars, Map<Name, Optional<TargetType>> declared, boolean returnsSelf) {
        MethodScope nested(boolean returnsSelf) {
            return new MethodScope(new HashMap<>(mappedVars), new HashMap<>(declared), returnsSelf);
        }
    }

    /** Fields mapped to a primitive functional interface, reachable as {@code this.f} or {@code other.f}. */
    private final Map<Name, Mapping> mappedFields = new HashMap<>();
    private MethodScope method = new MethodScope(new HashMap<>(), new HashMap<>(), false);
    private int lambdaDepth;
    private boolean shadowed;
    private boolean inSupertypes;

    Map<Name, Optional<TargetType>> declared() {
        return method.declared();
    }

    /** A type variable of this name is declared by the method or class being translated, so the template's is not in scope. */
    boolean shadowed() {
        return shadowed;
    }

    boolean inSupertypes() {
        return inSupertypes;
    }

    /** A {@code return} of the method itself, not of a lambda inside it, in a method returning the template's own type. */
    boolean returnsSelfHere() {
        return method.returnsSelf() && lambdaDepth == 0;
    }

    Optional<Mapping> mappedVar(Name name) {
        return Optional.ofNullable(method.mappedVars().get(name));
    }

    Optional<Mapping> mappedField(Name name) {
        return Optional.ofNullable(mappedFields.get(name));
    }

    void declareField(Name name, Optional<Mapping> mapping, Optional<TargetType> target) {
        mapping.ifPresent(m -> {
            method.mappedVars().put(name, m);
            mappedFields.put(name, m);
        });
        method.declared().put(name, target);
    }

    void declare(Name name, Optional<TargetType> target) {
        method.declared().put(name, target);
    }

    /** A declaration without a mapping hides an outer variable of the same name. */
    void map(Name name, Optional<Mapping> mapping) {
        mapping.ifPresentOrElse(m -> method.mappedVars().put(name, m), () -> method.mappedVars().remove(name));
    }

    void withShadowing(boolean shadowsT, Runnable action) {
        boolean saved = shadowed;
        shadowed |= shadowsT;
        try {
            action.run();
        } finally {
            shadowed = saved;
        }
    }

    /** {@code returnsSelf} is read inside the method's shadowing: its own {@code <T>} hides the template's. */
    void inMethod(boolean shadowsT, BooleanSupplier returnsSelf, Runnable action) {
        withShadowing(shadowsT, () -> {
            MethodScope saved = method;
            method = method.nested(returnsSelf.getAsBoolean());
            try {
                action.run();
            } finally {
                method = saved;
            }
        });
    }

    void inSupertypes(Runnable action) {
        inSupertypes = true;
        try {
            action.run();
        } finally {
            inSupertypes = false;
        }
    }

    void inLambda(Runnable action) {
        lambdaDepth++;
        try {
            action.run();
        } finally {
            lambdaDepth--;
        }
    }
}
