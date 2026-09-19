package dev.specialize.processor.inline;

import dev.specialize.InlineBody;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Name;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/** {@code @Inline} methods of this compilation and, lazily, of classes on the class path. */
public final class InlineRegistry {
    /** A method by owner, name and arity: what a call site can tell without types. */
    private record Member(String owner, String name, int arity) {
    }

    private final Elements elements;
    private final InlineMethodFactory factory;
    private final Set<String> loaded = new HashSet<>();
    private final Map<Member, List<InlineMethod>> inlines = new HashMap<>();
    /** How many methods (inline or not) share a member key; more than one makes a call ambiguous without types. */
    private final Map<Member, Integer> declared = new HashMap<>();

    public InlineRegistry(Elements elements, InlineMethodFactory factory) {
        this.elements = elements;
        this.factory = factory;
    }

    /** The unique inline method {@code owner.name} of the given arity; overloads that differ only in types are not inlined. */
    public Optional<InlineMethod> find(String owner, String name, int arity) {
        if (loaded.add(owner)) {
            loadFromClassFile(owner);
        }
        Member member = new Member(owner, name, arity);
        List<InlineMethod> candidates = inlines.getOrDefault(member, List.of());
        return candidates.size() == 1 && declared.getOrDefault(member, 0) == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private void add(Member member, Optional<InlineMethod> inline) {
        declared.merge(member, 1, Integer::sum);
        inline.ifPresent(m -> inlines.computeIfAbsent(member, _ -> new ArrayList<>()).add(m));
    }

    public void collect(JCCompilationUnit unit, NameResolver resolver) {
        unit.defs.stream().filter(JCClassDecl.class::isInstance).map(JCClassDecl.class::cast)
                .forEach(cd -> collect(cd, resolver.qualify(cd.name.toString()), resolver));
    }

    private void collect(JCClassDecl cd, String owner, NameResolver resolver) {
        loaded.add(owner);
        Map<Name, Boolean> publicStatics = publicStaticMembers(cd);
        for (JCTree def : cd.defs) {
            switch (def) {
                case JCClassDecl nested -> collect(nested, owner + "." + nested.name, resolver);
                case JCMethodDecl m -> add(new Member(owner, m.name.toString(), m.params.size()),
                        TreeUtil.hasAnnotation(m.mods.annotations, Annotations.INLINE_SIMPLE) ? factory.fromSource(m, owner, publicStatics, resolver) : Optional.empty());
                default -> { }
            }
        }
    }

    /** The class's static members by name, {@code true} only when every declaration of that name is public. */
    private static Map<Name, Boolean> publicStaticMembers(JCClassDecl cd) {
        Map<Name, Boolean> members = new HashMap<>();
        for (JCTree def : cd.defs) {
            switch (def) {
                case JCVariableDecl v when TreeUtil.isStatic(v.mods) -> members.merge(v.name, isPublic(v.mods), Boolean::logicalAnd);
                case JCMethodDecl m when TreeUtil.isStatic(m.mods) -> members.merge(m.name, isPublic(m.mods), Boolean::logicalAnd);
                case JCClassDecl nested -> members.merge(nested.name, isPublic(nested.mods), Boolean::logicalAnd);
                default -> { }
            }
        }
        return members;
    }

    private static boolean isPublic(JCModifiers mods) {
        return (mods.flags & Flags.PUBLIC) != 0;
    }

    private void loadFromClassFile(String owner) {
        Optional.ofNullable(elements.getTypeElement(owner)).ifPresent(type -> ElementFilter.methodsIn(type.getEnclosedElements()).forEach(m -> {
            String name = m.getSimpleName().toString();
            add(new Member(owner, name, m.getParameters().size()),
                    Optional.ofNullable(m.getAnnotation(InlineBody.class)).flatMap(stored -> factory.fromClassFile(owner, name, stored)));
        }));
    }
}
