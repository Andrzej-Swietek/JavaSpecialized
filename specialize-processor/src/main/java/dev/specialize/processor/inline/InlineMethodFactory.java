package dev.specialize.processor.inline;

import dev.specialize.InlineBody;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

/** Builds {@link InlineMethod}s from source (validating and stamping {@code @InlineBody}) and from class files. */
public final class InlineMethodFactory {
    private final TreeMaker make;
    private final Names names;
    private final Messager messager;
    private final InlineBodyParser parser;
    private final InlineBodyStamp stamp;

    public InlineMethodFactory(TreeMaker make, Names names, Messager messager, InlineBodyParser parser) {
        this.make = make;
        this.names = names;
        this.messager = messager;
        this.parser = parser;
        this.stamp = new InlineBodyStamp(make, names);
    }

    /** {@code publicStaticMembers}: the owner's static members by name, {@code true} when public. */
    public Optional<InlineMethod> fromSource(JCMethodDecl method, String owner, Map<Name, Boolean> publicStaticMembers, NameResolver resolver) {
        String where = "@Inline " + owner + "." + method.name + ": ";
        Optional<InlineMethod> result = InlineBodyValidator.problem(method).<Optional<InlineMethod>>map(problem -> {
            messager.printMessage(Diagnostic.Kind.ERROR, where + problem);
            return Optional.empty();
        }).orElseGet(() -> build(method, owner, publicStaticMembers, resolver, where));
        result.ifPresent(inline -> stamp.stamp(method, inline));
        return result;
    }

    public Optional<InlineMethod> fromClassFile(String owner, String name, InlineBody stored) {
        if (stored.params().length != stored.paramTypes().length) {
            return warn(owner, name, "params and paramTypes differ in length");
        }
        Optional<String> invalidType = Arrays.stream(stored.paramTypes()).filter(type -> !type.isEmpty() && parser.type(type).isEmpty()).findFirst();
        if (invalidType.isPresent()) {
            return warn(owner, name, "not a valid Java type: " + invalidType.get());
        }
        List<InlineMethod.Parameter> parameters = IntStream.range(0, stored.params().length)
                .mapToObj(i -> new InlineMethod.Parameter(stored.params()[i], parser.type(stored.paramTypes()[i])))
                .toList();
        return parser.body(stored.body())
                .map(body -> new InlineMethod(owner, name, parameters, parser.type(stored.returnType()), stored.isVoid(), body))
                .or(() -> warn(owner, name, "not a valid Java expression: " + stored.body()));
    }

    private Optional<InlineMethod> warn(String owner, String name, String problem) {
        messager.printMessage(Diagnostic.Kind.WARNING, "specialize: cannot parse @InlineBody of " + owner + "." + name + ": " + problem
                + "; calls to it are not inlined");
        return Optional.empty();
    }

    private Optional<InlineMethod> build(JCMethodDecl method, String owner, Map<Name, Boolean> publicStaticMembers,
                                         NameResolver resolver, String where) {
        Set<Name> typeVariables = method.typarams.stream().map(tp -> tp.name).collect(Collectors.toUnmodifiableSet());
        TreeCopier<Void> copier = new TreeCopier<>(make);
        List<InlineMethod.Parameter> parameters = method.params.stream()
                .map(p -> new InlineMethod.Parameter(p.name.toString(), parameterType(p, typeVariables, resolver, copier)))
                .toList();
        boolean isVoid = TreeUtil.isVoid(method.restype);
        Optional<JCExpression> returnType = !isVoid && TreeUtil.isPrimitiveTypeTree(method.restype)
                ? Optional.of(copier.copy(method.restype)) : Optional.empty();
        Set<Name> bound = Stream.of(typeVariables, method.params.stream().map(p -> p.name).collect(Collectors.toSet()), locals(method.body))
                .flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
        Qualifier qualifier = new Qualifier(make, names, owner, bound, publicStaticMembers, resolver);
        JCTree body = qualifier.translate(InlineBodyValidator.singleExpression(method).<JCTree>map(copier::copy).orElseGet(() -> copier.copy(method.body)));
        return qualifier.problem().<Optional<InlineMethod>>map(problem -> {
            messager.printMessage(Diagnostic.Kind.ERROR, where + problem);
            return Optional.empty();
        }).orElseGet(() -> Optional.of(new InlineMethod(owner, method.name.toString(), parameters, returnType, isVoid, body)));
    }

    private static Set<Name> locals(JCTree body) {
        Set<Name> locals = new HashSet<>();
        new TreeScanner() {
            @Override
            public void visitVarDef(JCVariableDecl local) {
                locals.add(local.name);
                super.visitVarDef(local);
            }
        }.scan(body);
        return locals;
    }

    private Optional<JCExpression> parameterType(JCVariableDecl param, Set<Name> typeVariables, NameResolver resolver, TreeCopier<Void> copier) {
        if (typeVariables.stream().anyMatch(tv -> TreeUtil.mentions(param.vartype, tv))) {
            return Optional.empty();
        }
        boolean[] unresolved = {false};
        JCExpression qualified = new TreeTranslator() {
            @Override
            public void visitIdent(JCIdent ident) {
                Optional<String> resolved = resolver.resolveTypeName(ident);
                unresolved[0] |= resolved.isEmpty();
                result = resolved.<JCExpression>map(q -> TreeUtil.qualIdent(make, names, q)).orElse(ident);
            }
        }.translate(copier.copy(param.vartype));
        return unresolved[0] ? Optional.empty() : Optional.of(qualified);
    }
}
