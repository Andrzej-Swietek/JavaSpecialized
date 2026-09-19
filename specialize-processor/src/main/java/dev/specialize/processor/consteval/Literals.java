package dev.specialize.processor.consteval;

import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import java.lang.reflect.Array;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Runtime values as source-level constant expressions: primitives, strings, enums, {@code null} and arrays of those. */
public final class Literals {
    private static final Set<Class<?>> LITERALS = Set.of(Integer.class, Long.class, Double.class, Float.class, Character.class, Boolean.class, String.class);

    private Literals() {
    }

    public static Optional<JCExpression> of(TreeMaker make, Names names, Object value) {
        return switch (value) {
            case null -> Optional.of(make.Literal(TypeTag.BOT, null));
            case Double d when d.isNaN() || d.isInfinite() -> Optional.of(constant(make, names, "java.lang.Double", d.isNaN(), d > 0));
            case Float f when f.isNaN() || f.isInfinite() -> Optional.of(constant(make, names, "java.lang.Float", f.isNaN(), f > 0));
            case Short s -> Optional.of(narrowed(make, TypeTag.SHORT, s));
            case Byte b -> Optional.of(narrowed(make, TypeTag.BYTE, b));
            case Enum<?> e -> Optional.of(make.Select(TreeUtil.qualIdent(make, names, e.getDeclaringClass().getName().replace('$', '.')),
                    names.fromString(e.name())));
            default -> LITERALS.contains(value.getClass()) ? Optional.of(make.Literal(value))
                    : value.getClass().isArray() ? array(make, names, value) : Optional.empty();
        };
    }

    /** javac's class writer expects {@code short} and {@code byte} constants as ints: {@code (short) 301}. */
    private static JCExpression narrowed(TreeMaker make, TypeTag tag, Number value) {
        return make.TypeCast(make.TypeIdent(tag), make.Literal(TypeTag.INT, value.intValue()));
    }

    private static JCExpression constant(TreeMaker make, Names names, String box, boolean nan, boolean positive) {
        String name = nan ? "NaN" : positive ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY";
        return make.Select(TreeUtil.qualIdent(make, names, box), names.fromString(name));
    }

    private static Optional<JCExpression> array(TreeMaker make, Names names, Object array) {
        List<JCExpression> elements = List.nil();
        for (int i = 0; i < Array.getLength(array); i++) {
            Optional<JCExpression> element = of(make, names, Array.get(array, i));
            if (element.isEmpty()) {
                return Optional.empty();
            }
            elements = elements.append(element.get());
        }
        return Optional.of(make.NewArray(typeTree(make, names, array.getClass().getComponentType()), List.nil(), elements));
    }

    private static JCExpression typeTree(TreeMaker make, Names names, Class<?> type) {
        if (type.isArray()) {
            return make.TypeArray(typeTree(make, names, type.getComponentType()));
        }
        return type.isPrimitive()
                ? make.TypeIdent(TypeTag.valueOf(type.getName().toUpperCase(Locale.ROOT)))
                : TreeUtil.qualIdent(make, names, type.getName().replace('$', '.'));
    }
}
