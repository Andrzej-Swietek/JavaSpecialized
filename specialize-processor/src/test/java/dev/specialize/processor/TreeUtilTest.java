package dev.specialize.processor;

import dev.specialize.processor.resolve.TreeUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class TreeUtilTest {
    private final Context context;
    private final ParserFactory parsers;
    private final TreeMaker make;
    private final Names names;

    TreeUtilTest() {
        ModuleAccess.ensureOpen();
        JavacTaskImpl task = (JavacTaskImpl) ToolProvider.getSystemJavaCompiler().getTask(null, null, null,
                java.util.List.of("-proc:none"), java.util.List.of("java.lang.Object"), null);
        context = task.getContext();
        parsers = ParserFactory.instance(context);
        make = TreeMaker.instance(context);
        names = Names.instance(context);
    }

    private JCExpression expr(String text) {
        return parsers.newParser(text, false, false, false).parseExpression();
    }

    private JCExpression type(String text) {
        return parsers.newParser(text, false, false, false).parseType();
    }

    @Test
    void namesAndPackages() {
        assertEquals("a.b.C", TreeUtil.flatten(expr("a.b.C")).orElseThrow());
        assertEquals("a.b.C", TreeUtil.flatten(type("@Deprecated a.b.C")).orElseThrow());
        assertTrue(TreeUtil.flatten(expr("a().b")).isEmpty());
        assertTrue(TreeUtil.flatten(expr("a[0]")).isEmpty());
        assertTrue(TreeUtil.flatten(null).isEmpty());
        assertEquals("C", TreeUtil.simpleName(expr("a.b.C")));
        assertEquals("C", TreeUtil.simpleName(expr("C")));
        assertEquals("C", TreeUtil.simpleName("a.b.C"));
        assertEquals("C", TreeUtil.simpleName("C"));
        assertEquals("a.b", TreeUtil.packageOf("a.b.C"));
        assertEquals("", TreeUtil.packageOf("C"));
        assertEquals("a.b.C", TreeUtil.qualIdent(make, names, "a.b.C").toString());
        assertEquals("C", TreeUtil.qualIdent(make, names, "C").toString());
    }

    @Test
    void pureArguments() {
        for (String pure : java.util.List.of("x", "((1))", "(int) x", "-x", "!b", "~x", "+x", "a + b * 2", "a << 2 | b",
                "c ? a : b", "o instanceof String", "this", "(long) (a - b)")) {
            assertTrue(TreeUtil.isPure(expr(pure)), pure);
        }
        for (String impure : java.util.List.of("x++", "f()", "a.b()", "a.b.c", "new Object()", "o instanceof String s", "x = 1",
                "arr[i]", "arr[f()]", "f()[0]", "() -> 1", "a + f()", "f() + a", "a / b", "a % b", "(String) o", "(int) f()", "c ? f() : b",
                "c ? a : f()", "f() ? a : b", "f() instanceof String")) {
            assertFalse(TreeUtil.isPure(expr(impure)), impure);
        }
    }

    @Test
    void typesWildcardsAndAnnotations() {
        assertTrue(TreeUtil.isUnboundedWildcard(((com.sun.tools.javac.tree.JCTree.JCTypeApply) type("List<?>")).arguments.head));
        assertFalse(TreeUtil.isUnboundedWildcard(((com.sun.tools.javac.tree.JCTree.JCTypeApply) type("List<? extends T>")).arguments.head));
        assertFalse(TreeUtil.isUnboundedWildcard(type("T")));
        assertTrue(TreeUtil.isPrimitiveTypeTree(type("int")));
        assertFalse(TreeUtil.isPrimitiveTypeTree(type("void")));
        assertFalse(TreeUtil.isPrimitiveTypeTree(type("String")));
        assertEquals(1, TreeUtil.asTypeApply(type("@Deprecated Opt<T>")).orElseThrow().arguments.size(), "annotation lands on the class name");
        assertTrue(TreeUtil.asTypeApply(type("Opt")).isEmpty());
        assertTrue(TreeUtil.mentions(type("List<T>"), names.fromString("T")));
        assertFalse(TreeUtil.mentions(type("List<U>"), names.fromString("T")));

        JCAnnotation inline = make.Annotation(TreeUtil.qualIdent(make, names, "dev.specialize.Inline"), List.nil());
        JCAnnotation boxed = make.Annotation(make.Ident(names.fromString("Boxed")), List.nil());
        assertTrue(TreeUtil.annotationNamed(inline, "Inline"));
        assertFalse(TreeUtil.annotationNamed(boxed, "Inline"));
        assertEquals(List.of(boxed), TreeUtil.without(List.of(inline, boxed), "Inline"));
    }
}
