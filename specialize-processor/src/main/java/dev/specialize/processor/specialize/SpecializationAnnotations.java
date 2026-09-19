package dev.specialize.processor.specialize;

import dev.specialize.processor.model.PrimitiveTarget;
import dev.specialize.processor.model.ReferenceTarget;
import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.model.Template;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

/** The annotations a generated class carries: {@code @Specialized(of, type|types, generated = true)} and {@code @GeneratedSpecialization}. */
record SpecializationAnnotations(TreeMaker make, Names names) {
    private static final String OF = "of";
    private static final String TYPE = "type";
    private static final String TYPES = "types";
    private static final String GENERATED = "generated";

    JCAnnotation specialized(Template template, TargetTuple tuple) {
        List<JCExpression> literals = tuple.targets().stream().map(this::classLiteral).collect(List.collector());
        JCExpression types = tuple.isSingle()
                ? attribute(TYPE, literals.head)
                : attribute(TYPES, make.NewArray(null, List.nil(), literals));
        return make.Annotation(TreeUtil.qualIdent(make, names, Annotations.SPECIALIZED), List.of(
                attribute(OF, make.Select(TreeUtil.qualIdent(make, names, template.qualified()), names._class)),
                types,
                attribute(GENERATED, TreeUtil.literal(make, true))));
    }

    JCAnnotation generated() {
        return make.Annotation(TreeUtil.qualIdent(make, names, Annotations.GENERATED), List.nil());
    }

    private JCExpression attribute(String name, JCExpression value) {
        return make.Assign(make.Ident(names.fromString(name)), value);
    }

    private JCExpression classLiteral(TargetType target) {
        return switch (target) {
            case PrimitiveTarget p -> make.Select(make.TypeIdent(p.tag()), names._class);
            case ReferenceTarget r -> make.Select(TreeUtil.qualIdent(make, names, r.key()), names._class);
        };
    }
}
