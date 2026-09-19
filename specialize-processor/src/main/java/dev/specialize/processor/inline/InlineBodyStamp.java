package dev.specialize.processor.inline;

import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

/** Replaces the method's {@code @InlineBody} with one describing {@code inline}, for callers compiling against the class file. */
record InlineBodyStamp(TreeMaker make, Names names) {

    void stamp(JCMethodDecl method, InlineMethod inline) {
        List<JCExpression> params = inline.parameters().stream().map(p -> (JCExpression) make.Literal(p.name())).collect(List.collector());
        List<JCExpression> types = inline.parameters().stream()
                .map(p -> (JCExpression) make.Literal(p.type().map(Object::toString).orElse(""))).collect(List.collector());
        List<JCExpression> arguments = List.of(
                attribute("params", make.NewArray(null, List.nil(), params)),
                attribute("paramTypes", make.NewArray(null, List.nil(), types)),
                attribute("returnType", make.Literal(inline.returnType().map(Object::toString).orElse(""))),
                attribute("body", make.Literal(inline.body().toString())),
                attribute("isVoid", TreeUtil.literal(make, inline.isVoid())));
        JCAnnotation annotation = make.at(method.pos).Annotation(TreeUtil.qualIdent(make, names, Annotations.INLINE_BODY), arguments);
        method.mods.annotations = TreeUtil.without(method.mods.annotations, Annotations.INLINE_BODY_SIMPLE).append(annotation);
    }

    private JCExpression attribute(String name, JCExpression value) {
        return make.Assign(make.Ident(names.fromString(name)), value);
    }
}
