package dev.specialize.processor.model;

import dev.specialize.processor.resolve.TreeUtil;


public record ReferenceTarget(String key) implements TargetType {
    @Override
    public String boxed() {
        return key;
    }

    @Override
    public String suffix() {
        return TreeUtil.simpleName(key);
    }
}
