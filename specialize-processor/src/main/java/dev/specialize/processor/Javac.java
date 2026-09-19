package dev.specialize.processor;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileManager;

/** The javac services the processor works with, and the trees behind elements. */
public record Javac(TreeMaker make, Names names, JavacTrees trees, Elements elements, Messager messager, Filer filer,
             ParserFactory parsers, Log log, JavaFileManager fileManager) {

    public static Javac of(ProcessingEnvironment env) {
        Context context = ProcessingEnvironments.unwrap(env).getContext();
        return new Javac(TreeMaker.instance(context), Names.instance(context), JavacTrees.instance(context),
                env.getElementUtils(), env.getMessager(), env.getFiler(), ParserFactory.instance(context), Log.instance(context),
                context.get(JavaFileManager.class));
    }

    public JCClassDecl treeOf(TypeElement type) {
        return (JCClassDecl) trees.getTree(type);
    }

    public JCMethodDecl methodOf(ExecutableElement method) {
        return (JCMethodDecl) trees.getTree(method);
    }

    public JCCompilationUnit unitOf(Element element) {
        return (JCCompilationUnit) trees.getPath(element).getCompilationUnit();
    }
}
