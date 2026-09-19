package dev.specialize.processor.specialize;

import dev.specialize.processor.Javac;
import dev.specialize.processor.model.ExplicitSpecialization;
import dev.specialize.processor.model.PrimitiveTarget;
import dev.specialize.processor.model.SourceTemplate;
import dev.specialize.processor.model.Specialization;
import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.model.Template.TypeParameter;
import dev.specialize.processor.registry.ClassIndex;
import dev.specialize.processor.registry.SpecRegistry;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;
import dev.specialize.processor.rewrite.UseSiteRewriter;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Injects {@code static OptInt some(int v) { return OptInt.some(v); }} into the generic template for every static
 * factory whose erasure changes under substitution, so {@code Opt.some(5)} resolves to the specialization by plain
 * overload resolution.
 */
public final class BridgeInjector {
    private static final long ACCESS_AND_STATIC = Flags.PUBLIC | Flags.PROTECTED | Flags.PRIVATE | Flags.STATIC;

    /** How one bridge differs from the factory it delegates for. */
    private record Shape(UnaryOperator<JCExpression> returnType, UnaryOperator<JCExpression> parameterType,
                         Function<JCVariableDecl, JCExpression> argument, Function<List<JCExpression>, JCMethodInvocation> call,
                         List<JCTree.JCAnnotation> annotations) {
    }

    private final TreeMaker make;
    private final Names names;
    private final SpecRegistry registry;
    private final Function<SourceTemplate, UseSiteRewriter> rewriters;

    public BridgeInjector(Javac javac, SpecRegistry registry, Function<SourceTemplate, UseSiteRewriter> rewriters) {
        this.make = javac.make();
        this.names = javac.names();
        this.registry = registry;
        this.rewriters = rewriters;
    }

    public void inject(SourceTemplate template, NameResolver resolver) {
        JCClassDecl cd = template.tree();
        Set<Name> tvars = template.specialized().stream().map(TypeParameter::name).map(names::fromString).collect(Collectors.toUnmodifiableSet());
        cd.defs.stream().filter(JCMethodDecl.class::isInstance).map(JCMethodDecl.class::cast)
                .map(MethodSignatures::signature).forEach(template::markBridge);
        java.util.List<Specialization> specs = Stream.of(
                        template.tuples().stream().map(t -> registry.specializationFor(template, t).orElseThrow()),
                        registry.explicitSpecializationsOf(template).stream().map(Specialization.class::cast),
                        template.requests().stream().map(Specialization.class::cast))
                .flatMap(Function.identity()).distinct().toList();
        UseSiteRewriter rewriter = rewriters.apply(template);
        java.util.List<JCTree> added = cd.defs.stream()
                .filter(def -> def instanceof JCMethodDecl m && !template.isInjected(m) && isFactory(m, tvars, resolver))
                .map(JCMethodDecl.class::cast)
                .flatMap(factory -> bridgesFor(template, factory, specs, tvars, resolver))
                .map(rewriter::translate)
                .map(JCTree.class::cast)
                .toList();
        added.forEach(template::markInjected);
        cd.defs = cd.defs.appendList(List.from(added));
    }

    /** Per specialization a delegating bridge, then guards for the primitives a single-variable template leaves out. */
    private Stream<JCMethodDecl> bridgesFor(SourceTemplate template, JCMethodDecl factory, java.util.List<Specialization> specs,
                                            Set<Name> tvars, NameResolver resolver) {
        Stream<JCMethodDecl> perSpec = specs.stream().flatMap(spec -> bridge(template, factory, spec, tvars, resolver).stream());
        boolean guarded = tvars.size() == 1 && specs.stream().anyMatch(spec -> spec.type().single() instanceof PrimitiveTarget);
        Stream<JCMethodDecl> guards = guarded
                ? PrimitiveTarget.all().stream()
                        .filter(primitive -> specs.stream().noneMatch(spec -> spec.type().single().equals(primitive)))
                        .flatMap(primitive -> guard(template, factory, primitive, tvars.iterator().next()).stream())
                : Stream.empty();
        return Stream.concat(perSpec, guards);
    }

    /**
     * {@code static Opt<Character> some(char v) { return some((Character) v); }}, so {@code Opt.some('c')} does not
     * widen to {@code OptInt.some(int)}. Only for factories whose {@code T} parameters are plain {@code T}
     * ({@code T[]} cannot be boxed element-wise).
     */
    private Optional<JCMethodDecl> guard(SourceTemplate template, JCMethodDecl factory, PrimitiveTarget primitive, Name tvar) {
        if (factory.params.stream().anyMatch(p -> TreeUtil.mentions(p.vartype, tvar) && !(p.vartype instanceof JCIdent))) {
            return Optional.empty();
        }
        TemplateSpecializer boxed = new TemplateSpecializer(make, names, template, TargetTuple.of(TargetType.reference(primitive.boxed())),
                template.qualified(), _ -> false);
        TemplateSpecializer prim = new TemplateSpecializer(make, names, template, TargetTuple.of(primitive), template.qualified(), _ -> false);
        JCTree.JCAnnotation keepGeneric = make.Annotation(TreeUtil.qualIdent(make, names, Annotations.BOXED), List.nil()); // stays generic at use sites
        Shape shape = new Shape(boxed::translate, prim::translate,
                p -> p.vartype instanceof JCIdent id && id.name == tvar
                        ? make.TypeCast(TreeUtil.qualIdent(make, names, primitive.boxed()), make.Ident(p.name))
                        : make.Ident(p.name),
                args -> make.Apply(List.nil(), make.Ident(factory.name), args),
                List.of(keepGeneric));
        return assemble(template, factory, Set.of(tvar), shape);
    }

    private Optional<JCMethodDecl> bridge(SourceTemplate template, JCMethodDecl factory, Specialization spec, Set<Name> tvars,
                                          NameResolver resolver) {
        if (spec instanceof ExplicitSpecialization explicit && !explicit.declaresStatic(factory.name.toString(), factory.params.size())) {
            return Optional.empty();
        }
        TemplateSpecializer specializer = new TemplateSpecializer(make, names, template, spec.type(), spec.qualified(), registry.templateRef(resolver));
        Shape shape = new Shape(specializer::translate, specializer::translate, p -> make.Ident(p.name),
                args -> make.Apply(List.nil(), make.Select(TreeUtil.qualIdent(make, names, spec.qualified()), factory.name), args),
                List.nil());
        return assemble(template, factory, tvars, shape);
    }

    private Optional<JCMethodDecl> assemble(SourceTemplate template, JCMethodDecl factory, Set<Name> droppedTypeParameters, Shape shape) {
        make.at(factory.pos);
        JCMethodDecl bridge = new TreeCopier<Void>(make).copy(factory);
        bridge.typarams = bridge.typarams.stream().filter(tp -> !droppedTypeParameters.contains(tp.name)).collect(List.collector());
        bridge.restype = shape.returnType().apply(bridge.restype);
        List<JCExpression> args = bridge.params.map(p -> {
            JCExpression arg = shape.argument().apply(p);
            p.vartype = shape.parameterType().apply(p.vartype);
            p.mods = make.Modifiers(p.mods.flags & Flags.PARAMETER);
            return arg;
        });
        if (!template.markBridge(MethodSignatures.signature(bridge))) {
            return Optional.empty();
        }
        bridge.mods = make.Modifiers(factory.mods.flags & ACCESS_AND_STATIC, shape.annotations());
        JCMethodInvocation call = shape.call().apply(args);
        bridge.body = make.Block(0, List.of(TreeUtil.isVoid(factory.restype) ? make.Exec(call) : make.Return(call)));
        return Optional.of(bridge);
    }

    /** {@code static <T> R m(..)} using a specialized type variable's name, with a parameter whose erasure changes. */
    private boolean isFactory(JCMethodDecl method, Set<Name> tvars, NameResolver resolver) {
        boolean concreteStatic = TreeUtil.isStatic(method.mods) && method.body != null && !ClassIndex.isVarargs(method);
        return concreteStatic && method.typarams.stream().anyMatch(tp -> tvars.contains(tp.name))
                && method.params.stream().anyMatch(p -> changesErasure(p.vartype, tvars, resolver));
    }

    /** {@code T}, {@code T[]}, or {@code Opt<T>} / {@code Map2<K, V>} where the class is itself a template. */
    private boolean changesErasure(JCTree type, Set<Name> tvars, NameResolver resolver) {
        return switch (type) {
            case JCIdent id -> tvars.contains(id.name);
            case JCArrayTypeTree array -> changesErasure(array.elemtype, tvars, resolver);
            case JCTypeApply ta -> ta.arguments.stream().anyMatch(arg -> arg instanceof JCIdent id && tvars.contains(id.name))
                    && registry.templateRef(resolver).test(ta.clazz);
            default -> false;
        };
    }
}
