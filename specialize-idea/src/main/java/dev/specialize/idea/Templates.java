package dev.specialize.idea;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** What the IDE needs to know about {@code @Specialize} templates, read from the PSI. */
final class Templates {
    static final String SPECIALIZE = "dev.specialize.Specialize";
    static final String SPECIALIZED = "dev.specialize.Specialized";
    private static final String DEFAULT_NAME_PATTERN = "{Name}{Type}";

    private Templates() {
    }

    static boolean isTemplate(PsiClass psiClass) {
        return psiClass.hasAnnotation(SPECIALIZE);
    }

    /**
     * The template a type argument belongs to: {@code Opt} in {@code Opt<int>} or {@code Opt.<int>some(..)}, or the
     * template that {@code standsFor} the class ({@code List<int>} with {@code MyList standsFor List}).
     */
    static Optional<PsiClass> templateOfTypeArgument(PsiElement typeArgument) {
        PsiReferenceParameterList arguments = PsiTreeUtil.getParentOfType(typeArgument, PsiReferenceParameterList.class);
        Optional<PsiClass> owner = switch (arguments == null ? null : arguments.getParent()) {
            case PsiReferenceExpression call -> Optional.ofNullable(call.getQualifierExpression())
                    .filter(PsiReferenceExpression.class::isInstance)
                    .map(qualifier -> ((PsiReferenceExpression) qualifier).resolve())
                    .flatMap(Templates::asClass);
            case PsiJavaCodeReferenceElement type -> asClass(type.resolve());
            case null, default -> Optional.empty();
        };
        return owner.flatMap(cls -> isTemplate(cls) ? Optional.of(cls) : standingFor(cls));
    }

    private static Optional<PsiClass> asClass(PsiElement element) {
        return element instanceof PsiClass cls ? Optional.of(cls) : Optional.empty();
    }

    private static Optional<PsiClass> classOf(PsiType type) {
        return type instanceof PsiClassType classType ? Optional.ofNullable(classType.resolve()) : Optional.empty();
    }

    /** A template declaring {@code standsFor = {…, cls, …}} anywhere in the project or its libraries. */
    static Optional<PsiClass> standingFor(PsiClass cls) {
        return CachedValuesManager.getCachedValue(cls, () ->
                CachedValueProvider.Result.create(searchStandingFor(cls), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static Optional<PsiClass> searchStandingFor(PsiClass cls) {
        String qualified = cls.getQualifiedName();
        Project project = cls.getProject();
        PsiClass annotation = JavaPsiFacade.getInstance(project).findClass(SPECIALIZE, GlobalSearchScope.allScope(project));
        if (qualified == null || annotation == null) {
            return Optional.empty();
        }
        return AnnotatedElementsSearch.searchPsiClasses(annotation, GlobalSearchScope.allScope(project)).findAll().stream()
                .filter(template -> standsFor(template).contains(qualified))
                .findFirst();
    }

    private static List<String> standsFor(PsiClass template) {
        return Optional.ofNullable(template.getAnnotation(SPECIALIZE))
                .map(a -> a.findAttributeValue("standsFor"))
                .stream()
                .flatMap(value -> value instanceof PsiArrayInitializerMemberValue array ? Arrays.stream(array.getInitializers()) : Stream.of(value))
                .filter(PsiClassObjectAccessExpression.class::isInstance)
                .map(v -> ((PsiClassObjectAccessExpression) v).getOperand().getType())
                .flatMap(type -> classOf(type).stream())
                .map(PsiClass::getQualifiedName)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Whether an error text names a specialization ({@code OptInt}) together with its template ({@code Opt}): the
     * processor makes those the same type, so "required Opt&lt;Integer&gt;, provided OptInt" is not an error.
     */
    static boolean mentionsSpecializationAndTemplate(String description, Project project) {
        List<String> words = Arrays.stream(description.split("[^A-Za-z0-9_$]+")).filter(w -> !w.isEmpty()).toList();
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
        return words.stream()
                .flatMap(word -> Arrays.stream(cache.getClassesByName(word, GlobalSearchScope.allScope(project))))
                .flatMap(cls -> templateOf(cls).stream())
                .anyMatch(template -> words.contains(template.getName()));
    }

    /** The template a {@code @Specialized(of = …)} class was generated from. */
    static Optional<PsiClass> templateOf(PsiClass specialization) {
        return Optional.ofNullable(specialization.getAnnotation(SPECIALIZED))
                .map(a -> a.findAttributeValue("of"))
                .filter(PsiClassObjectAccessExpression.class::isInstance)
                .map(v -> ((PsiClassObjectAccessExpression) v).getOperand().getType())
                .flatMap(Templates::classOf);
    }

    /** {@code OptInt} for {@code Opt} and {@code int}, if the generated class is already part of the project. */
    static Optional<PsiClass> generated(PsiClass template, String suffix) {
        String qualified = template.getQualifiedName();
        if (qualified == null) {
            return Optional.empty();
        }
        String pattern = Optional.ofNullable(template.getAnnotation(SPECIALIZE))
                .map(a -> AnnotationUtil.getStringAttributeValue(a, "namePattern"))
                .orElse(DEFAULT_NAME_PATTERN);
        int dot = qualified.lastIndexOf('.');
        String pkg = dot < 0 ? "" : qualified.substring(0, dot + 1);
        String name = pattern.replace("{Name}", template.getName()).replace("{Type}", suffix);
        return Optional.ofNullable(JavaPsiFacade.getInstance(template.getProject()).findClass(pkg + name, template.getResolveScope()));
    }

    static String suffix(PsiType primitive) {
        return StringUtil.capitalize(primitive.getCanonicalText());
    }
}
