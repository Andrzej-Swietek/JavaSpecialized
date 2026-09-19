package dev.specialize.processor.specialize;

import dev.specialize.Prim;
import dev.specialize.processor.model.PrimitiveTarget;
import dev.specialize.processor.model.ReferenceTarget;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The {@code dev.specialize.Prim} helpers and the primitive expression each one becomes in a specialization. */
public enum PrimHelper {
    ZERO("zero"), EQ("eq"), HASH("hash"), STR("str"), COMPARE("compare"), NEW_ARRAY("newArray"), IS_NULL("isNull"),
    IS_PRIMITIVE("isPrimitive"), TYPE("type"), BOX("box"), WRITE("write"), READ("read"), PUT("put"), GET("get"), BYTES("bytes");

    private static final Set<String> OWNERS = Set.of(Prim.class.getSimpleName(), Prim.class.getName());
    private static final Map<String, PrimHelper> BY_METHOD = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(h -> h.methodName, Function.identity()));

    /** What a helper needs to build its replacement. */
    public record Ctx(TreeMaker make, Names names, PrimitiveTarget target) {
        JCExpression qualified(String name) {
            return TreeUtil.qualIdent(make, names, name);
        }

        Name name(String name) {
            return names.fromString(name);
        }

        JCExpression call(JCExpression receiver, String method, List<JCExpression> args) {
            return make.Apply(List.nil(), make.Select(receiver, name(method)), args);
        }

        JCExpression boxCall(String method, List<JCExpression> args) {
            return call(qualified(target.boxed()), method, args);
        }

        JCExpression zero() {
            return switch (target.tag()) {
                case INT -> make.Literal(TypeTag.INT, 0);
                case LONG -> make.Literal(TypeTag.LONG, 0L);
                case DOUBLE -> make.Literal(TypeTag.DOUBLE, 0.0d);
                case FLOAT -> make.Literal(TypeTag.FLOAT, 0.0f);
                case BOOLEAN -> TreeUtil.literal(make, false);
                case CHAR -> make.Literal(TypeTag.CHAR, 0);
                default -> make.TypeCast(make.TypeIdent(target.tag()), make.Literal(TypeTag.INT, 0));
            };
        }
    }

    private final String methodName;

    PrimHelper(String methodName) {
        this.methodName = methodName;
    }

    /** The arguments that are values of type {@code T} (as opposed to streams, buffers or lengths). */
    public List<JCExpression> valueArguments(List<JCExpression> args) {
        return switch (this) {
            case EQ, COMPARE -> args;
            case HASH, STR, IS_NULL, BOX -> List.of(args.head);
            case WRITE, PUT -> List.of(args.tail.head);
            case ZERO, NEW_ARRAY, IS_PRIMITIVE, TYPE, READ, GET, BYTES -> List.nil();
        };
    }

    JCExpression rewrite(Ctx c, List<JCExpression> args) {
        TreeMaker make = c.make;
        return switch (this) {
            case ZERO -> c.zero();
            case EQ -> c.target.isFloating()
                    ? make.Parens(make.Binary(JCTree.Tag.EQ, c.boxCall("compare", args), make.Literal(TypeTag.INT, 0)))
                    : make.Parens(make.Binary(JCTree.Tag.EQ, args.head, args.tail.head));
            case HASH -> c.boxCall("hashCode", args);
            case STR -> make.Apply(List.nil(), make.Select(c.qualified("java.lang.String"), c.name("valueOf")), args);
            case COMPARE -> c.boxCall("compare", args);
            case NEW_ARRAY -> make.NewArray(make.TypeIdent(c.target.tag()), List.of(args.head), null);
            case IS_NULL -> TreeUtil.literal(make, false);
            case IS_PRIMITIVE -> TreeUtil.literal(make, true);
            case TYPE -> make.Select(make.TypeIdent(c.target.tag()), c.names._class);
            case BOX -> c.boxCall("valueOf", args);
            case WRITE -> c.call(args.head, "write" + c.target.suffix(), List.of(args.tail.head));
            case READ -> c.call(args.head, "read" + c.target.suffix(), List.nil());
            case PUT -> switch (c.target.tag()) {
                case BOOLEAN -> c.call(args.head, "put", List.of(make.TypeCast(make.TypeIdent(TypeTag.BYTE),
                        make.Parens(make.Conditional(args.tail.head, make.Literal(TypeTag.INT, 1), make.Literal(TypeTag.INT, 0))))));
                case BYTE -> c.call(args.head, "put", List.of(args.tail.head));
                default -> c.call(args.head, "put" + c.target.suffix(), List.of(args.tail.head));
            };
            case GET -> switch (c.target.tag()) {
                case BOOLEAN -> make.Parens(make.Binary(JCTree.Tag.NE, c.call(args.head, "get", List.nil()), make.Literal(TypeTag.INT, 0)));
                case BYTE -> c.call(args.head, "get", List.nil());
                default -> c.call(args.head, "get" + c.target.suffix(), List.nil());
            };
            case BYTES -> c.target.tag() == TypeTag.BOOLEAN
                    ? make.Literal(TypeTag.INT, 1)
                    : make.Select(c.qualified(c.target.boxed()), c.name("BYTES"));
        };
    }

    /** Reference targets keep the generic helpers, except where erasure would not produce the declared type. */
    public Optional<JCExpression> rewriteReference(TreeMaker make, Names names, ReferenceTarget target, List<JCExpression> args) {
        return switch (this) {
            case NEW_ARRAY -> Optional.of(make.NewArray(TreeUtil.qualIdent(make, names, target.key()), List.of(args.head), null));
            case TYPE -> Optional.of(make.Select(TreeUtil.qualIdent(make, names, target.key()), names._class));
            default -> Optional.empty();
        };
    }

    /** The helper a call's method expression denotes, when it is {@code Prim.x} or {@code dev.specialize.Prim.x}. */
    public static Optional<PrimHelper> of(JCExpression method) {
        return method instanceof JCFieldAccess access
                ? TreeUtil.flatten(access.selected).filter(OWNERS::contains).flatMap(_ -> Optional.ofNullable(BY_METHOD.get(access.name.toString())))
                : Optional.empty();
    }
}
