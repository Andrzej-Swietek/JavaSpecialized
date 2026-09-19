package dev.specialize.idea;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.Arrays;
import java.util.List;

/** What the IDE sees with the plugin: bridge overloads on templates, no errors on {@code Opt<int>}. */
public class SpecializePluginTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("package dev.specialize; public @interface Specialize { Class<?>[] types() default {}; String namePattern() default \"{Name}{Type}\"; Class<?>[] standsFor() default {}; }");
        myFixture.addClass("package java.lang; public final class Integer extends Number { }");   // the light fixture's mock JDK is minimal
        myFixture.addClass("package t; public interface Pred<T> { boolean test(T value); }");   // the light fixture's mock JDK has no java.util
        myFixture.addClass("package t; public class Bag<T> { }");
        myFixture.addClass("package t; public class Pair<A, B> { }");
        myFixture.addClass("package t; import dev.specialize.Specialize; @Specialize(types = int.class, standsFor = Bag.class) public final class MyList<T> { "
                + "public void add(T item) { } public T get(int i) { return null; } }");
        myFixture.addClass("package t; import dev.specialize.Specialize; @Specialize(types = int.class) public final class Opt<T> { "
                + "public static <T> Opt<T> some(T value) { return null; } public static <T> Opt<T> empty() { return null; } "
                + "public static <T> Opt<T> ofAll(T[] values) { return null; } public static <T> Opt<T> keep(T... values) { return null; } "
                + "public T get() { return null; } public Opt<T> filter(Pred<T> p) { return this; } }");
        myFixture.addClass("package dev.specialize; public @interface Specialized { Class<?> of(); Class<?> type() default void.class; boolean generated() default false; }");
        myFixture.addClass("package t; import dev.specialize.Specialized; @Specialized(of = Opt.class, type = int.class, generated = true) "
                + "public final class OptInt { public static OptInt some(int value) { return null; } public static OptInt empty() { return null; } public int get() { return 0; } }");
    }

    public void testBridgesAreVisibleOnTheTemplate() {
        PsiClass opt = myFixture.findClass("t.Opt");
        List<String> signatures = Arrays.stream(opt.findMethodsByName("some", false))
                .map(m -> m.getReturnType().getPresentableText() + " some(" + m.getParameterList().getParameters()[0].getType().getPresentableText() + ")")
                .sorted().toList();
        assertEquals(List.of("Opt<T> some(T)", "OptInt some(int)"), signatures);
        PsiMethod[] ofAll = opt.findMethodsByName("ofAll", false);
        assertEquals("int[] parameters become int[]", 2, ofAll.length);
        assertEquals("varargs factories are not bridged", 1, opt.findMethodsByName("keep", false).length);
        assertEquals("only int is specialized here", 0, Arrays.stream(opt.findMethodsByName("some", false))
                .filter(m -> m.getParameterList().getParameters()[0].getType().getPresentableText().equals("long")).count());
    }

    public void testPrimitiveTypeArgumentsAreNotErrors() {
        myFixture.configureByText("Use.java", """
                package t;
                public class Use {
                    Opt<int> field = Opt.some(5);
                    OptInt viaBridge = Opt.some(5);
                    int read() { return field.get(); }
                    Opt<int> filtered() { return field.filter(x -> x > 1); }
                    Bag<int> alias = new Bag<>();
                    Opt<int> explicit = Opt.<int>some(7);
                    Opt<Integer> boxedSpelling(int v) { return v > 0 ? Opt.some(v) : Opt.empty(); }
                    OptInt viaTemplate() { return Opt.some(1); }
                }
                """);
        List<HighlightInfo> errors = myFixture.doHighlighting().stream().filter(i -> i.getSeverity() == HighlightSeverity.ERROR).toList();
        assertEquals("no errors: " + errors.stream().map(HighlightInfo::getDescription).toList(), 0, errors.size());
    }

    public void testOtherClassesKeepTheirErrors() {
        myFixture.configureByText("Plain.java", """
                package t;
                public class Plain {
                    Pair<int, String> xs;
                    String s = 5;
                }
                """);
        List<String> errors = myFixture.doHighlighting().stream().filter(i -> i.getSeverity() == HighlightSeverity.ERROR)
                .map(HighlightInfo::getDescription).toList();
        assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.toLowerCase().contains("primitive")));
        assertTrue(errors.toString(), errors.stream().anyMatch(e -> e.contains("String")));
    }
}
