package dev.specialize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConstTest {
    @Test
    void withoutTheProcessorEvalIsIdentity() {
        assertEquals(42, Const.eval(42));
        assertEquals("x", Const.eval("x"));
    }
}
