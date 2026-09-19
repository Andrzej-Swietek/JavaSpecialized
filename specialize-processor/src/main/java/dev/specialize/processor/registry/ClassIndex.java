package dev.specialize.processor.registry;

import dev.specialize.processor.model.Specialization;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/** Constructors and methods of every class in this compilation (by tree) and on the class path (by element), by qualified owner name. */
public final class ClassIndex {
    public record Source(JCClassDecl decl, NameResolver resolver) {
    }

    private final Elements elements;
    private final Names names;
    private final SpecRegistry registry;
    private final Map<String, Source> sources = new HashMap<>();

    public ClassIndex(Elements elements, Names names, SpecRegistry registry) {
        this.elements = elements;
        this.names = names;
        this.registry = registry;
    }

    public void index(JCCompilationUnit unit, NameResolver resolver) {
        unit.defs.stream().filter(JCClassDecl.class::isInstance).map(JCClassDecl.class::cast)
                .forEach(cd -> index(cd, resolver.qualify(cd.name.toString()), resolver));
    }

    private void index(JCClassDecl cd, String qualified, NameResolver resolver) {
        sources.put(qualified, new Source(cd, resolver));
        cd.defs.stream().filter(JCClassDecl.class::isInstance).map(JCClassDecl.class::cast)
                .forEach(nested -> index(nested, qualified + "." + nested.name, resolver));
    }

    public boolean contains(String qualified) {
        return sources.containsKey(qualified);
    }

    public boolean declaresMethod(String owner, String name) {
        return Optional.ofNullable(sources.get(owner))
                .map(source -> source.decl().defs.stream().anyMatch(def -> def instanceof JCMethodDecl m && m.name.contentEquals(name)))
                .orElse(false);
    }

    /** javac keeps the varargs flag on the last parameter of the tree, not on the method's modifiers. */
    public static boolean isVarargs(JCMethodDecl method) {
        return method.params.nonEmpty() && (method.params.last().mods.flags & Flags.VARARGS) != 0;
    }

    /**
     * Per parameter of the unique constructor ({@code method} empty) or method of that name and arity: the
     * specialization it expects. Empty when there is no such unique callee.
     */
    /** The specialization a parameter type written in source denotes, resolved in that file's scope. */
    @FunctionalInterface
    public interface SpecOfType {
        Optional<Specialization> of(JCTree type, NameResolver resolver);
    }

    public Optional<List<Optional<Specialization>>> parameterSpecs(String owner, Optional<String> method, int arity,
                                                                   SpecOfType specOfSourceType) {
        return Optional.ofNullable(sources.get(owner))
                .map(source -> fromSource(source, method, arity, specOfSourceType))
                .orElseGet(() -> Optional.ofNullable(elements.getTypeElement(owner)).flatMap(type -> fromElement(type, method, arity)));
    }

    private Optional<List<Optional<Specialization>>> fromSource(Source source, Optional<String> method, int arity,
                                                                SpecOfType specOf) {
        Name wanted = method.map(names::fromString).orElse(names.init);
        List<JCMethodDecl> candidates = source.decl().defs.stream()
                .filter(def -> def instanceof JCMethodDecl m && m.name == wanted && accepts(m.params.size(), isVarargs(m), arity))
                .map(JCMethodDecl.class::cast)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.ofNullable(source.decl().extending)
                    .flatMap(superType -> source.resolver().resolveTypeName(TreeUtil.rawType(superType)))
                    .flatMap(superOwner -> parameterSpecs(superOwner, method, arity, specOf));
        }
        return unique(candidates).map(m -> IntStream.range(0, arity)
                .mapToObj(i -> parameterType(m, i).flatMap(type -> specOf.of(type, source.resolver())))
                .toList());
    }

    private static boolean accepts(int declared, boolean varargs, int arity) {
        return varargs ? arity >= declared - 1 : arity == declared;
    }

    /** The declared type for argument {@code i} (the element type of the trailing array for varargs overflow); empty for a {@code @Boxed} parameter. */
    private static Optional<JCTree> parameterType(JCMethodDecl m, int i) {
        JCVariableDecl param = m.params.get(Math.min(i, m.params.size() - 1));
        if (TreeUtil.isBoxed(param.mods)) {
            return Optional.empty();
        }
        boolean spread = isVarargs(m) && i >= m.params.size() - 1;
        return Optional.of(spread ? ((JCArrayTypeTree) param.vartype).elemtype : param.vartype);
    }

    private static TypeMirror parameterType(ExecutableElement e, int i) {
        VariableElement param = e.getParameters().get(Math.min(i, e.getParameters().size() - 1));
        boolean spread = e.isVarArgs() && i >= e.getParameters().size() - 1;
        return spread ? ((ArrayType) param.asType()).getComponentType() : param.asType();
    }

    private Optional<List<Optional<Specialization>>> fromElement(TypeElement type, Optional<String> method, int arity) {
        List<ExecutableElement> pool = method.isPresent()
                ? ElementFilter.methodsIn(type.getEnclosedElements())
                : ElementFilter.constructorsIn(type.getEnclosedElements());
        List<ExecutableElement> candidates = pool.stream()
                .filter(e -> method.map(e.getSimpleName()::contentEquals).orElse(true))
                .filter(e -> accepts(e.getParameters().size(), e.isVarArgs(), arity))
                .toList();
        if (candidates.isEmpty() && type.getSuperclass() instanceof DeclaredType superType) {
            return fromElement((TypeElement) superType.asElement(), method, arity);
        }
        return unique(candidates).map(e -> IntStream.range(0, arity).mapToObj(i -> specOfMirror(parameterType(e, i))).toList());
    }

    private static <T> Optional<T> unique(List<T> candidates) {
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    /** A class-file parameter typed {@code OptInt}; one typed {@code Opt<Integer>} (compiled without the processor) wants the generic class. */
    private Optional<Specialization> specOfMirror(TypeMirror type) {
        return type instanceof DeclaredType declared && declared.getTypeArguments().isEmpty()
                ? registry.specByName(((TypeElement) declared.asElement()).getQualifiedName().toString())
                : Optional.empty();
    }
}
