package dev.specialize.processor.model;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import dev.specialize.BoxedArguments;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;

/**
 * A template whose source is available: compiled here (generated from, bridges injected) or recovered from the
 * resource a library compilation stored (generated from on request only, see {@code @SpecializeWith}).
 */
public final class SourceTemplate implements Template {
    private final BinaryTemplate declaration;
    private final JCClassDecl tree;
    private final JCCompilationUnit unit;
    private final boolean compiledHere;
    private final Set<TargetTuple> scanned = new LinkedHashSet<>();
    private final Set<TargetTuple> generated = new HashSet<>();
    private final Set<GeneratedSpecialization> requests = new LinkedHashSet<>();
    private final Set<String> bridgeSignatures = new HashSet<>();
    private final Set<JCTree> injectedBridges = Collections.newSetFromMap(new IdentityHashMap<>());

    private SourceTemplate(BinaryTemplate declaration, JCClassDecl tree, JCCompilationUnit unit, boolean compiledHere) {
        this.declaration = declaration;
        this.tree = tree;
        this.unit = unit;
        this.compiledHere = compiledHere;
    }

    public static SourceTemplate compiledHere(BinaryTemplate declaration, JCClassDecl tree, JCCompilationUnit unit) {
        return new SourceTemplate(declaration, tree, unit, true);
    }

    /** A library's template: its declared specializations exist already, only requests are generated. */
    public static SourceTemplate fromLibrary(BinaryTemplate declaration, JCClassDecl tree, JCCompilationUnit unit) {
        SourceTemplate template = new SourceTemplate(declaration, tree, unit, false);
        template.generated.addAll(declaration.tuples());
        return template;
    }

    public boolean compiledHere() {
        return compiledHere;
    }

    public JCClassDecl tree() {
        return tree;
    }

    public JCCompilationUnit unit() {
        return unit;
    }

    /** Specializations another module asked for with {@code @SpecializeWith}, generated next to the requesting type. */
    public Set<GeneratedSpecialization> requests() {
        return Collections.unmodifiableSet(requests);
    }

    public void request(GeneratedSpecialization spec) {
        requests.add(spec);
    }

    /** {@code true} when the tuple was neither declared nor scanned before. */
    public boolean addScanned(TargetTuple tuple) {
        return !declaration.tuples().contains(tuple) && scanned.add(tuple);
    }

    /** {@code true} the first time a tuple is asked for, so each specialization is written once. */
    public boolean markGenerated(TargetTuple tuple) {
        return generated.add(tuple);
    }

    /** {@code false} when a bridge with this signature was already added. */
    public boolean markBridge(String signature) {
        return bridgeSignatures.add(signature);
    }

    public boolean isInjected(JCTree bridge) {
        return injectedBridges.contains(bridge);
    }

    public void markInjected(JCTree bridge) {
        injectedBridges.add(bridge);
    }

    @Override
    public String qualified() {
        return declaration.qualified();
    }

    @Override
    public String namePattern() {
        return declaration.namePattern();
    }

    @Override
    public List<TypeParameter> parameters() {
        return declaration.parameters();
    }

    @Override
    public List<TargetTuple> tuples() {
        return Stream.concat(declaration.tuples().stream(), scanned.stream()).toList();
    }

    @Override
    public boolean autoscan() {
        return declaration.autoscan();
    }

    @Override
    public BoxedArguments boxedArguments() {
        return declaration.boxedArguments();
    }

    @Override
    public Optional<TypeElement> element() {
        return declaration.element();
    }
}
