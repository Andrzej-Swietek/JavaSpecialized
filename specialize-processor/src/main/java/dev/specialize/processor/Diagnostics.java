package dev.specialize.processor;

import com.sun.tools.javac.tree.JCTree;

/** Reports an error at a tree of the code being rewritten. */
@FunctionalInterface
public interface Diagnostics {
    void error(JCTree at, String message);
}
