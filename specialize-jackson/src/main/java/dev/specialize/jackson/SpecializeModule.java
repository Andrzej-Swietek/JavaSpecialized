package dev.specialize.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Register it ({@code mapper.registerModule(new SpecializeModule())}, {@code findAndRegisterModules()}, or a Spring
 * {@code @Bean}) and every class carrying {@code @Specialize} / {@code @Specialized} with an {@code @Absent} factory
 * reads JSON {@code null} and missing fields as that value. Serialization and the value itself are the template's
 * business: annotate it with {@code @JsonValue} / {@code @JsonCreator}, the specializations inherit them.
 */
public final class SpecializeModule extends SimpleModule {

    public SpecializeModule() {
        super("specialize");
        setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription description,
                                                          JsonDeserializer<?> deserializer) {
                return AbsentValues.of(description.getBeanClass())
                        .<JsonDeserializer<?>>map(absent -> new AbsentAwareDeserializer(deserializer, absent))
                        .orElse(deserializer);
            }
        });
    }
}
