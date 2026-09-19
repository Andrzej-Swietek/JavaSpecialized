package dev.specialize.idea;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Shows the bridges the processor injects into a template: for {@code static <T> Opt<T> some(T v)} and every
 * primitive the template is specialized for, a {@code static OptInt some(int v)} — once {@code OptInt} exists in the
 * project (after the first build, like Lombok's generated members after the annotation is resolved).
 */
public final class BridgeAugmentProvider extends PsiAugmentProvider {
    private static final List<PsiPrimitiveType> PRIMITIVES = List.of(PsiTypes.intType(), PsiTypes.longType(), PsiTypes.doubleType(),
            PsiTypes.booleanType(), PsiTypes.byteType(), PsiTypes.shortType(), PsiTypes.charType(), PsiTypes.floatType());

    @Override
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type, String nameHint) {
        if (!(element instanceof PsiClass psiClass) || type != PsiMethod.class || !Templates.isTemplate(psiClass)) {
            return List.of();
        }
        List<PsiMethod> bridges = CachedValuesManager.getCachedValue(psiClass, () ->
                CachedValueProvider.Result.create(bridges(psiClass), PsiModificationTracker.MODIFICATION_COUNT));
        List<PsiMethod> requested = Optional.ofNullable(nameHint)
                .map(name -> bridges.stream().filter(bridge -> bridge.getName().equals(name)).toList())
                .orElse(bridges);
        @SuppressWarnings("unchecked")
        List<Psi> result = (List<Psi>) requested;
        return result;
    }

    /** Multi-parameter templates keep their generic factories in the IDE; javac still bridges them. */
    private static List<PsiMethod> bridges(PsiClass template) {
        PsiTypeParameter[] typeParameters = template.getTypeParameters();
        if (typeParameters.length != 1) {
            return List.of();
        }
        String variable = typeParameters[0].getName();
        List<PsiMethod> factories = ownMethods(template).stream().filter(method -> isFactory(method, variable)).toList();
        return PRIMITIVES.stream()
                .flatMap(primitive -> Templates.generated(template, Templates.suffix(primitive)).stream()
                        .flatMap(generated -> factories.stream().map(factory -> bridge(template, generated, factory, variable, primitive))))
                .toList();
    }

    /** The methods written in the source: {@code getMethods()} would include the augments and ask this provider again. */
    private static List<PsiMethod> ownMethods(PsiClass template) {
        return template instanceof PsiExtensibleClass extensible ? extensible.getOwnMethods() : Arrays.asList(template.getMethods());
    }

    /** {@code static <T> R m(..)} using the class type variable's name, with a parameter of type {@code T} or {@code T[]}. */
    private static boolean isFactory(PsiMethod method, String variable) {
        return method.hasModifierProperty(PsiModifier.STATIC) && !method.isVarArgs()
                && Arrays.stream(method.getTypeParameters()).anyMatch(tp -> variable.equals(tp.getName()))
                && Arrays.stream(method.getParameterList().getParameters()).anyMatch(p -> mentions(p.getType(), variable));
    }

    private static boolean mentions(PsiType type, String variable) {
        return switch (type) {
            case PsiArrayType array -> mentions(array.getComponentType(), variable);
            case PsiClassType classType -> variable.equals(classType.getClassName()) && classType.resolve() instanceof PsiTypeParameter;
            default -> false;
        };
    }

    private static PsiMethod bridge(PsiClass template, PsiClass generated, PsiMethod factory, String variable, PsiPrimitiveType primitive) {
        PsiElementFactory factoryOf = PsiElementFactory.getInstance(template.getProject());
        LightMethodBuilder bridge = new LightMethodBuilder(template.getManager(), factory.getName())
                .setContainingClass(template)
                .addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
        bridge.setNavigationElement(factory);
        PsiClassType self = factoryOf.createType(generated);
        bridge.setMethodReturnType(substitute(factory.getReturnType(), template, variable, primitive, self));
        for (PsiParameter parameter : factory.getParameterList().getParameters()) {
            bridge.addParameter(parameter.getName(), substitute(parameter.getType(), template, variable, primitive, self));
        }
        return bridge;
    }

    /** {@code T} → {@code int}, {@code T[]} → {@code int[]}, {@code Opt<T>} → {@code OptInt}, {@code List<T>} → {@code List<Integer>}. */
    private static PsiType substitute(PsiType type, PsiClass template, String variable, PsiPrimitiveType primitive, PsiClassType self) {
        return switch (type) {
            case null -> PsiTypes.voidType();
            case PsiArrayType array -> substitute(array.getComponentType(), template, variable, primitive, self).createArrayType();
            case PsiClassType classType when mentions(classType, variable) -> primitive;
            case PsiClassType classType when isSelf(classType, template, variable) -> self;
            case PsiClassType classType -> boxedArguments(classType, variable, primitive);
            default -> type;
        };
    }

    private static boolean isSelf(PsiClassType type, PsiClass template, String variable) {
        PsiType[] arguments = type.getParameters();
        return template.getManager().areElementsEquivalent(type.resolve(), template)
                && arguments.length == 1 && mentions(arguments[0], variable);
    }

    private static PsiType boxedArguments(PsiClassType type, String variable, PsiPrimitiveType primitive) {
        PsiClass resolved = type.resolve();
        if (resolved == null) {
            return type;
        }
        PsiType[] arguments = type.getParameters();
        PsiTypeParameter[] parameters = resolved.getTypeParameters();
        if (arguments.length != parameters.length) {
            return type;
        }
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        for (int i = 0; i < arguments.length; i++) {
            PsiType argument = arguments[i];
            PsiType boxed = Optional.ofNullable(argument).filter(a -> mentions(a, variable))
                    .<PsiType>map(a -> primitive.getBoxedType(resolved)).orElse(argument);
            substitutor = substitutor.put(parameters[i], boxed);
        }
        return PsiElementFactory.getInstance(resolved.getProject()).createType(resolved, substitutor);
    }
}
