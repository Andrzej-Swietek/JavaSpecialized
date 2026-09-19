package dev.specialize.processor.rewrite;

import dev.specialize.processor.model.Specialization;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import java.util.Optional;
import java.util.stream.IntStream;

/** How a specialization is spelled at a use site, and how expressions are retargeted to it. */
record SpecUse(TreeMaker make, Names names, SpecLookup lookup) {

    JCExpression ref(Specialization spec) {
        return TreeUtil.qualIdent(make, names, spec.qualified());
    }

    /** {@code Map2<Integer, String>} → {@code Map2Int<String>}: the generic positions keep their arguments. */
    JCExpression type(Specialization spec, List<JCExpression> typeArguments) {
        List<JCExpression> kept = List.from(spec.template().genericArguments(typeArguments));
        return kept.isEmpty() ? ref(spec) : make.TypeApply(ref(spec), kept);
    }

    Retargeter retargeter(Specialization spec) {
        String template = spec.template().qualified();
        return new Retargeter(make, tree -> lookup.namesTemplate(tree, template), name -> lookup.callsTemplateFactory(name.toString(), template),
                () -> ref(spec), !spec.template().genericParameterNames().isEmpty());
    }

    JCExpression retarget(Specialization spec, JCExpression expression) {
        return retargeter(spec).retarget(expression);
    }

    /** Each argument retargeted to the specialization its parameter is declared with. */
    List<JCExpression> retargetedArguments(List<JCExpression> args, java.util.List<Optional<Specialization>> specs) {
        return IntStream.range(0, args.size())
                .mapToObj(i -> specs.get(i).map(spec -> retarget(spec, args.get(i))).orElse(args.get(i)))
                .collect(List.collector());
    }
}
