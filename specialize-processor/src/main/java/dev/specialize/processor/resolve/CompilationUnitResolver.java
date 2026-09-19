package dev.specialize.processor.resolve;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotatedType;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCImport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Resolves a unit's type names through its top-level classes, imports, package and {@code java.lang}; anything else is empty. */
public final class CompilationUnitResolver implements NameResolver {
    private static final String ON_DEMAND = "*";

    /** What the import section declares. */
    private record Imports(Map<String, String> single, List<String> onDemand, Map<String, String> staticSingle, List<String> staticOnDemand) {
        static Imports of(List<JCImport> imports) {
            Map<String, String> single = new HashMap<>();
            Map<String, String> staticSingle = new HashMap<>();
            List<String> onDemand = new ArrayList<>();
            List<String> staticOnDemand = new ArrayList<>();
            for (JCImport imp : imports) {
                JCFieldAccess selected = (JCFieldAccess) imp.qualid;
                String owner = TreeUtil.flatten(selected.selected).orElseThrow();
                String member = selected.name.toString();
                if (member.equals(ON_DEMAND)) {
                    (imp.staticImport ? staticOnDemand : onDemand).add(owner);
                } else if (imp.staticImport) {
                    staticSingle.put(member, owner);
                } else {
                    single.put(member, owner + "." + member);
                }
            }
            return new Imports(Map.copyOf(single), List.copyOf(onDemand), Map.copyOf(staticSingle), List.copyOf(staticOnDemand));
        }
    }

    private final String pkg;
    private final Imports imports;
    private final Set<String> topLevelNames;
    private final Predicate<String> typeExists;
    private final Map<String, Optional<String>> cache = new HashMap<>();

    public CompilationUnitResolver(JCCompilationUnit unit, Predicate<String> typeExists) {
        this.typeExists = typeExists;
        this.pkg = Optional.ofNullable(unit.getPackage()).flatMap(p -> TreeUtil.flatten(p.pid)).orElse("");
        this.imports = Imports.of(unit.defs.stream().filter(JCImport.class::isInstance).map(JCImport.class::cast).toList());
        this.topLevelNames = unit.defs.stream().filter(JCClassDecl.class::isInstance).map(def -> ((JCClassDecl) def).name.toString())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String qualify(String simpleName) {
        return pkg.isEmpty() ? simpleName : pkg + "." + simpleName;
    }

    @Override
    public Optional<String> staticImportOwner(String member) {
        return Optional.ofNullable(imports.staticSingle().get(member));
    }

    @Override
    public List<String> staticOnDemandOwners() {
        return imports.staticOnDemand();
    }

    @Override
    public Optional<String> resolveTypeName(JCTree tree) {
        return switch (tree) {
            case JCAnnotatedType annotated -> resolveTypeName(annotated.underlyingType);
            case JCIdent ident -> resolveSimple(ident.name.toString());
            case JCFieldAccess access -> TreeUtil.flatten(access).flatMap(this::resolveQualified);
            case null, default -> Optional.empty();
        };
    }

    private Optional<String> resolveQualified(String flat) {
        if (typeExists.test(flat)) {
            return Optional.of(flat);
        }
        int dot = flat.indexOf('.');
        return resolveSimple(flat.substring(0, dot)).map(head -> head + flat.substring(dot)).filter(typeExists);
    }

    private Optional<String> resolveSimple(String simple) {
        return cache.computeIfAbsent(simple, this::lookup);
    }

    private Optional<String> lookup(String simple) {
        return topLevel(simple)
                .or(() -> Optional.ofNullable(imports.single().get(simple)))
                .or(() -> Stream.concat(Stream.of(qualify(simple)),
                                Stream.concat(imports.onDemand().stream().map(od -> od + "." + simple), Stream.of("java.lang." + simple)))
                        .filter(typeExists).findFirst());
    }

    private Optional<String> topLevel(String simple) {
        return topLevelNames.contains(simple) ? Optional.of(qualify(simple)) : Optional.empty();
    }
}
