package dev.specialize.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.specialize.InlineBody;
import org.junit.jupiter.api.Test;

/** {@code MathX} is on the class path here, so inlining runs from the {@code @InlineBody} metadata in the class file. */
class InlineTest {

    @Test
    void semanticsArePreserved() {
        InlineSubjects s = new InlineSubjects();
        assertEquals(25, InlineSubjects.CONSTANT);
        assertEquals(49, s.square(7));
        assertEquals(2.5, s.halfOfInt(5), "argument is cast to the parameter type (double), no integer division");
        assertEquals(4096L, s.kilobytes());
        assertEquals(5.0, s.lerpHalf());
        assertEquals(10, s.clamp(15));
        assertEquals(25, s.sumOfSquares(3, 4));
        assertEquals("x", InlineSubjects.orDefault(null, "x"));
        assertEquals(9, s.tripled(3));
        assertEquals(1, s.noDoubleEvaluation());
        assertEquals(1, s.counter, "between(next(), ..) uses 'v' twice: call kept, next() evaluated once");
        assertEquals(6 + 100, s.normal(2));
    }

    @Test
    void callsAreGoneFromTheBytecode() {
        String javap = Bytecode.of(InlineSubjects.class);

        assertFalse(javap.contains("MathX.sq"), javap);
        assertFalse(javap.contains("MathX.kb"), javap);
        assertFalse(javap.contains("MathX.lerp"), javap);
        assertFalse(javap.contains("MathX.clamp"), javap);
        assertFalse(javap.contains("MathX.sumOfSquares"), javap);
        assertFalse(javap.contains("MathX.half"), javap);
        assertFalse(javap.contains("MathX.orDefault"), javap);
        assertFalse(javap.contains("Method localTriple"), javap);

        // sq(3) + sq(4) became the compile-time constant 25: no static initializer writes CONSTANT
        assertFalse(javap.contains("putstatic"), javap);

        assertTrue(Bytecode.method(javap, "int square(int)").contains("imul"), javap);
        assertTrue(Bytecode.method(javap, "double halfOfInt(int)").contains("i2d"), "int argument widened to double: " + javap);
        assertTrue(Bytecode.method(javap, "long kilobytes()").contains("long 4096l"), "kb(4) folded: " + javap);
        assertTrue(Bytecode.method(javap, "double lerpHalf()").contains("double 5.0d"), "lerp(0, 10, 0.5) folded: " + javap);
        String clamp = Bytecode.method(javap, "int clamp(int)");
        assertTrue(clamp.contains("Math.max") && clamp.contains("Math.min"), "clamp body expanded inline: " + clamp);
        assertFalse(Bytecode.method(javap, "int normal(int)").contains("MathX.normal"), "block bodies are inlined too");
        assertFalse(Bytecode.method(javap, "void trace(int)").contains("MathX.trace"), "void block bodies become plain blocks");
        assertTrue(Bytecode.method(javap, "int noDoubleEvaluation()").contains("MathX.between"),
                "call with a non-simple argument used twice is kept: " + javap);
    }

    @Test
    void tailRecursionRunsAsALoop() {
        assertEquals(21, MathX.gcd(1071, 462));
        assertEquals(500_000_500_000L, MathX.sumTo(1_000_000, 0), "a million frames deep as recursion; a loop here");
        String javap = Bytecode.of(MathX.class);
        assertFalse(Bytecode.method(javap, "public static long sumTo(long, long)").contains("Method sumTo"), javap);
        assertFalse(Bytecode.method(javap, "public static long gcd(long, long)").contains("Method gcd"), javap);
    }

    @Test
    void classFileCarriesTheBodyForConsumers() throws Exception {
        InlineBody body = MathX.class.getMethod("sq", int.class).getAnnotation(InlineBody.class);
        assertEquals("x * x", body.body());
        assertEquals("int", body.returnType());
        InlineBody kb = MathX.class.getMethod("kb", long.class).getAnnotation(InlineBody.class);
        assertEquals("n << dev.specialize.examples.MathX.KB_SHIFT", kb.body(), "static members are qualified");
        InlineBody clamp = MathX.class.getMethod("clamp", int.class, int.class, int.class).getAnnotation(InlineBody.class);
        assertEquals("java.lang.Math.max(lo, java.lang.Math.min(hi, v))", clamp.body(), "types are qualified");
        InlineBody generic = MathX.class.getMethod("orDefault", Object.class, Object.class).getAnnotation(InlineBody.class);
        assertEquals("", generic.paramTypes()[0], "type-variable parameters get no cast");
    }
}
