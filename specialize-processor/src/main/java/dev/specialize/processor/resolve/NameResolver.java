package dev.specialize.processor.resolve;

import com.sun.tools.javac.tree.JCTree;
import java.util.List;
import java.util.Optional;

/** Resolves the names a compilation unit uses, before attribution. */
public interface NameResolver {

    /** The qualified name a type tree denotes; empty for wildcards, type variables, nested generics and an absent ({@code null}) tree. */
    Optional<String> resolveTypeName(JCTree tree);

    /** {@code pkg.Simple} for a top-level class of this unit's package. */
    String qualify(String simpleName);

    /** The class a member was statically imported from, by member name. */
    Optional<String> staticImportOwner(String member);

    /** The classes whose statics are imported on demand. */
    List<String> staticOnDemandOwners();
}
