package dev.specialize.processor.rewrite;

import dev.specialize.processor.model.Specialization;

import com.sun.tools.javac.util.Name;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The specialized variables in scope and what the enclosing method returns. */
record VariableScope(Map<Name, Specialization> declared, Optional<Specialization> returnSpec) {
    static VariableScope empty() {
        return new VariableScope(new HashMap<>(), Optional.empty());
    }

    VariableScope nested(Optional<Specialization> returnSpec) {
        return new VariableScope(new HashMap<>(declared), returnSpec);
    }

    /** A declaration of a non-specialized variable hides an outer specialized one of the same name. */
    void declare(Name name, Optional<Specialization> spec) {
        spec.ifPresentOrElse(s -> declared.put(name, s), () -> declared.remove(name));
    }

    Optional<Specialization> of(Name name) {
        return Optional.ofNullable(declared.get(name));
    }

    boolean holds(Name name) {
        return declared.containsKey(name);
    }
}
