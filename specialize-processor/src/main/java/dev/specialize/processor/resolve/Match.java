package dev.specialize.processor.resolve;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

/** A scanner answering whether something occurs in a tree; subclasses set {@link #found}. */
public abstract class Match extends TreeScanner {
    protected boolean found;

    public static boolean in(JCTree tree, Match match) {
        match.scan(tree);
        return match.found;
    }
}
