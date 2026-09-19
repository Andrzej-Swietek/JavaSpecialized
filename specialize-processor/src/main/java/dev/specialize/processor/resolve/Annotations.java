package dev.specialize.processor.resolve;

import dev.specialize.Boxed;
import dev.specialize.ConstEval;
import dev.specialize.GeneratedSpecialization;
import dev.specialize.Inline;
import dev.specialize.InlineBody;
import dev.specialize.PrimitiveArgument;
import dev.specialize.Specialize;
import dev.specialize.SpecializeWith;
import dev.specialize.Specialized;
import dev.specialize.TailRec;
import dev.specialize.Unroll;

/** Names of the API annotations as trees spell them: simple names before attribution, qualified names for generated code. */
public final class Annotations {
    public static final String SPECIALIZE = Specialize.class.getName();
    public static final String SPECIALIZE_PARAM = Specialize.Param.class.getCanonicalName();
    public static final String SPECIALIZED = Specialized.class.getName();
    public static final String SPECIALIZE_WITH = SpecializeWith.class.getName();
    public static final String GENERATED = GeneratedSpecialization.class.getName();
    public static final String BOXED = Boxed.class.getName();
    public static final String INLINE_BODY = InlineBody.class.getName();
    public static final String PRIMITIVE_ARGUMENT = PrimitiveArgument.class.getName();

    public static final String SPECIALIZE_SIMPLE = Specialize.class.getSimpleName();
    public static final String BOXED_SIMPLE = Boxed.class.getSimpleName();
    public static final String INLINE_SIMPLE = Inline.class.getSimpleName();
    public static final String INLINE_BODY_SIMPLE = InlineBody.class.getSimpleName();
    public static final String TAIL_REC_SIMPLE = TailRec.class.getSimpleName();
    public static final String UNROLL_SIMPLE = Unroll.class.getSimpleName();
    public static final String CONST_EVAL_SIMPLE = ConstEval.class.getSimpleName();
    public static final String PRIMITIVE_ARGUMENT_SIMPLE = PrimitiveArgument.class.getSimpleName();
    public static final String OVERRIDE_SIMPLE = Override.class.getSimpleName();

    private Annotations() {
    }
}
