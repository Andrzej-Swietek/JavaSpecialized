package dev.specialize.processor.resolve;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** The qualified names of the classes enclosing the tree being visited, innermost first. */
public final class ClassScope {
    private final NameResolver resolver;
    private final Deque<String> stack = new ArrayDeque<>();

    public ClassScope(NameResolver resolver) {
        this.resolver = resolver;
    }

    public void enter(String simpleName) {
        stack.push(stack.isEmpty() ? resolver.qualify(simpleName) : stack.peek() + "." + simpleName);
    }

    public void leave() {
        stack.pop();
    }

    public List<String> enclosingClasses() {
        return List.copyOf(stack);
    }
}
