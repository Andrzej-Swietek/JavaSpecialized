package dev.specialize.idea;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Hides the errors that {@code Opt<int>} produces in a file using a {@code @Specialize} template, which the
 * processor compiles to {@code OptInt}: "type argument cannot be of primitive type", every follow-up error whose
 * text names a primitive type argument, and every mismatch between a template and one of its specializations
 * ({@code Opt<Integer>} vs {@code OptInt}).
 */
public final class PrimitiveArgumentErrorFilter implements HighlightInfoFilter {
    private static final Pattern PRIMITIVE_ARGUMENT = Pattern.compile("[<,]\\s*(int|long|double|boolean|byte|short|char|float)\\s*[>,\\[]");
    /** {@code Opt.<int>some(7)}: with {@code T = int} the generic method and the bridge look identical to the IDE. */
    private static final Pattern SAME_SIGNATURE_TWICE = Pattern.compile("Ambiguous method call: both '(.+?)' and '\\1' match");

    @Override
    public boolean accept(HighlightInfo info, PsiFile file) {
        if (file == null || info.getSeverity() != HighlightSeverity.ERROR) {
            return true;
        }
        Optional<String> description = Optional.ofNullable(info.getDescription());
        if (description.filter(PrimitiveArgumentErrorFilter::mentionsPrimitiveArgument).isPresent() && hasTemplates(file)) {
            return false;
        }
        if (description.filter(text -> Templates.mentionsSpecializationAndTemplate(text, file.getProject())).isPresent()) {
            return false;
        }
        return !primitiveArgumentOfTemplate(file.findElementAt(info.getStartOffset()));
    }

    private static boolean mentionsPrimitiveArgument(String description) {
        return PRIMITIVE_ARGUMENT.matcher(description).find() || SAME_SIGNATURE_TWICE.matcher(description).find();
    }

    /** Only files that actually use a template somewhere; anything else keeps its errors untouched. */
    private static boolean hasTemplates(PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                PsiTreeUtil.findChildrenOfType(file, PsiTypeElement.class).stream().anyMatch(typeElement ->
                        typeElement.getType() instanceof PsiPrimitiveType && Templates.templateOfTypeArgument(typeElement).isPresent()),
                PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static boolean primitiveArgumentOfTemplate(PsiElement at) {
        Optional<PsiTypeElement> argument = Optional.ofNullable(at)
                .map(PsiElement::getParent)
                .filter(PsiTypeElement.class::isInstance)
                .map(PsiTypeElement.class::cast)
                .filter(typeElement -> typeElement.getType() instanceof PsiPrimitiveType);
        return argument.flatMap(Templates::templateOfTypeArgument).isPresent();
    }
}
