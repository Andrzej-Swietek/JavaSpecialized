package dev.specialize.processor.model;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import java.util.Optional;

/** Hand-written and annotated {@code @Specialized}; {@code source} is present when it is compiled in this run. */
public record ExplicitSpecialization(String qualified, Template template, TargetTuple type, Optional<JCClassDecl> source)
        implements Specialization {

    /** Whether a factory bridge {@code Template.name(...)} can delegate to {@code static name(<arity params>)} here. */
    public boolean declaresStatic(String name, int arity) {
        return source.stream().flatMap(decl -> decl.defs.stream()).anyMatch(def -> isStatic(def, name, arity));
    }

    private static boolean isStatic(JCTree def, String name, int arity) {
        return def instanceof JCMethodDecl m && m.name.contentEquals(name) && m.params.size() == arity
                && (m.mods.flags & Flags.STATIC) != 0;
    }
}
