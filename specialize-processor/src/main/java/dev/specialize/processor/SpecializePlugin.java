package dev.specialize.processor;

import dev.specialize.processor.specialize.PrimitiveArgumentRewriter;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;

/**
 * Started by javac automatically when the processor jar is on the processor path (no {@code -Xplugin} needed).
 * Makes {@code Opt<int>} legal everywhere by rewriting parsed trees before javac attributes any signature.
 */
public final class SpecializePlugin implements Plugin {

    @Override
    public String getName() {
        return "Specialize";
    }

    @Override
    public boolean autoStart() {
        return true;
    }

    @Override
    public void init(JavacTask task, String... args) {
        ModuleAccess.ensureOpen();
        PrimitiveArgumentRewriter.install(task);
    }
}
