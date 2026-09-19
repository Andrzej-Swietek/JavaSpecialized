package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asks for the specializations of the given templates for the annotated class or record, even when the templates live
 * in another library:
 *
 * <pre>{@code
 * @SpecializeWith({Opt.class, OptCodec.class})
 * public record UserDTO(String name, int age) { }
 * }</pre>
 *
 * generates {@code OptUserDTO} and {@code OptCodecUserDTO} next to {@code UserDTO} (in its package), and every
 * {@code Opt<UserDTO>} in this and later compilations becomes {@code OptUserDTO}. The processor stores each template's
 * source as {@code META-INF/specialize/<template>.java} when the library is compiled, which is what makes this
 * possible; a template from a library compiled without the processor cannot be specialized here.
 * Bridges ({@code Opt.some(userDto)}) are only injected when the template is compiled in the same module; elsewhere
 * use {@code OptUserDTO.some(dto)} or a typed target ({@code Opt<UserDTO> u = Opt.some(dto)} is retargeted).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpecializeWith {
    Class<?>[] value();
}
