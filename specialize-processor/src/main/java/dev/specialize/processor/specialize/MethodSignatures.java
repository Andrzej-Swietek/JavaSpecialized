package dev.specialize.processor.specialize;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Erasure-level identity of methods, as their parameter types are spelled in the tree. */
public final class MethodSignatures {
    private MethodSignatures() {
    }

    public static String signature(JCMethodDecl method) {
        return method.name + "(" + method.params.stream().map(p -> p.vartype.toString()).collect(Collectors.joining(",")) + ")";
    }

    /** A hand-written {@code static OptInt some(int)} in the template duplicates the specialized {@code some(T)}: drop it. */
    static List<JCTree> withoutColliding(List<JCTree> defs, Set<JCTree> handWritten) {
        Set<String> seen = defs.stream()
                .filter(def -> def instanceof JCMethodDecl && !handWritten.contains(def))
                .map(def -> signature((JCMethodDecl) def))
                .collect(Collectors.toCollection(HashSet::new));
        return defs.stream().filter(def -> !handWritten.contains(def) || seen.add(signature((JCMethodDecl) def))).collect(List.collector());
    }

    /** javac adds default and canonical record constructors to the tree without their bodies' field assignments; it regenerates them for a copy. */
    static boolean isImplicitConstructor(JCTree def) {
        return def instanceof JCMethodDecl m && (m.mods.flags & Flags.GENERATEDCONSTR) != 0;
    }
}
