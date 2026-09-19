package dev.specialize.processor.resolve;

import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotatedType;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCInstanceOf;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCPattern;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCWildcard;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Optional;

/** Side-effect free helpers over javac trees. */
public final class TreeUtil {
    public static final String BOXED = Annotations.BOXED_SIMPLE;

    private TreeUtil() {
    }

    /** {@code a.b.C} for an identifier / field-access chain. */
    public static Optional<String> flatten(JCTree tree) {
        return switch (tree) {
            case JCIdent id -> Optional.of(id.name.toString());
            case JCFieldAccess fa -> flatten(fa.selected).map(head -> head + "." + fa.name);
            case JCAnnotatedType at -> flatten(at.underlyingType);
            case null, default -> Optional.empty();
        };
    }

    /** Last segment of an annotation type name: {@code Inline} for {@code @dev.specialize.Inline}. */
    public static String simpleName(JCTree annotationType) {
        return annotationType instanceof JCIdent id ? id.name.toString() : ((JCFieldAccess) annotationType).name.toString();
    }

    public static String simpleName(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    public static String packageOf(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? "" : qualified.substring(0, dot);
    }

    public static JCExpression qualIdent(TreeMaker make, Names names, String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot < 0
                ? make.Ident(names.fromString(qualified))
                : make.Select(qualIdent(make, names, qualified.substring(0, dot)), names.fromString(qualified.substring(dot + 1)));
    }

    /**
     * Total and free of side effects: names, literals, and operators over those that cannot throw. Calls, field and
     * array reads, division, reference casts, assignments, {@code i++}, {@code new} and lambdas are not.
     */
    public static boolean isPure(JCExpression expression) {
        return switch (expression) {
            case JCParens p -> isPure(p.expr);
            case JCIdent _ -> true;
            case JCLiteral _ -> true;
            case JCTypeCast tc -> isPrimitiveTypeTree(tc.clazz) && isPure(tc.expr);
            case JCInstanceOf io -> !(io.pattern instanceof JCPattern) && isPure(io.expr);
            case JCBinary b -> switch (b.getTag()) {
                case DIV, MOD -> false;
                default -> isPure(b.lhs) && isPure(b.rhs);
            };
            case JCConditional c -> isPure(c.cond) && isPure(c.truepart) && isPure(c.falsepart);
            case JCUnary u -> switch (u.getTag()) {
                case NEG, POS, COMPL, NOT -> isPure(u.arg);
                default -> false;
            };
            default -> false;
        };
    }

    public static boolean isUnboundedWildcard(JCExpression expression) {
        return expression instanceof JCWildcard w && w.kind.kind == BoundKind.UNBOUND;
    }

    public static boolean annotationNamed(JCAnnotation annotation, String simpleName) {
        return simpleName.equals(simpleName(annotation.annotationType));
    }

    /** Whether a type argument was written as a primitive ({@code Opt<int>}) and marked by the plugin. */
    public static boolean isMarkedPrimitive(JCExpression typeArgument) {
        return typeArgument instanceof JCAnnotatedType at && hasAnnotation(at.annotations, Annotations.PRIMITIVE_ARGUMENT_SIMPLE);
    }

    public static boolean hasAnnotation(List<JCAnnotation> annotations, String simpleName) {
        return annotations.stream().anyMatch(a -> annotationNamed(a, simpleName));
    }

    public static boolean isBoxed(JCModifiers mods) {
        return hasAnnotation(mods.annotations, Annotations.BOXED_SIMPLE);
    }

    public static List<JCAnnotation> without(List<JCAnnotation> annotations, String simpleName) {
        return annotations.stream().filter(a -> !annotationNamed(a, simpleName)).collect(List.collector());
    }

    public static Optional<JCTypeApply> asTypeApply(JCTree tree) {
        return tree instanceof JCTypeApply ta ? Optional.of(ta) : Optional.empty();
    }

    /** {@code List} for {@code List<T>}; the tree itself otherwise. */
    public static JCTree rawType(JCTree type) {
        return type instanceof JCTypeApply ta ? ta.clazz : type;
    }

    /** {@code T} for {@code T[][]}; the tree itself otherwise. */
    public static JCTree elementType(JCTree type) {
        return type instanceof JCArrayTypeTree array ? elementType(array.elemtype) : type;
    }

    public static JCExpression unparenthesized(JCExpression expression) {
        return expression instanceof JCParens parens ? unparenthesized(parens.expr) : expression;
    }

    /** The expression safe to splice into another one: names and literals as they are, anything else in parentheses. */
    public static JCExpression parenthesized(TreeMaker make, JCExpression expression) {
        return expression instanceof JCIdent || expression instanceof JCLiteral ? expression : make.Parens(expression);
    }

    public static boolean mentions(JCTree tree, Name name) {
        return Match.in(tree, new Match() {
            @Override
            public void visitIdent(JCIdent ident) {
                found |= ident.name == name;
            }
        });
    }

    public static boolean isPrimitiveTypeTree(JCTree tree) {
        return tree instanceof JCPrimitiveTypeTree p && p.typetag != TypeTag.VOID;
    }

    public static boolean isVoid(JCTree returnType) {
        return returnType instanceof JCPrimitiveTypeTree p && p.typetag == TypeTag.VOID;
    }

    public static boolean isStatic(JCModifiers mods) {
        return (mods.flags & Flags.STATIC) != 0;
    }

    public static JCLiteral literal(TreeMaker make, boolean value) {
        return make.Literal(TypeTag.BOOLEAN, value ? 1 : 0);
    }
}
