package dev.specialize.processor.inline;

import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.Log;
import java.util.Optional;
import java.util.function.Function;

/** Parses the strings an {@code @InlineBody} stores; anything the parser reports is an empty result. */
public final class InlineBodyParser {
    private final ParserFactory parsers;
    private final Log log;

    public InlineBodyParser(ParserFactory parsers, Log log) {
        this.parsers = parsers;
        this.log = log;
    }

    public Optional<JCExpression> type(String source) {
        return source.isEmpty() ? Optional.empty() : parse(source, JavacParser::parseType);
    }

    /** A stored body: a block when it starts with {@code {}, an expression otherwise. */
    public Optional<JCTree> body(String source) {
        return source.startsWith("{")
                ? parse(source, JavacParser::parseStatement).filter(JCBlock.class::isInstance).map(JCTree.class::cast)
                : parse(source, JavacParser::parseExpression).map(JCTree.class::cast);
    }

    private <T extends JCTree> Optional<T> parse(String source, Function<JavacParser, T> production) {
        Log.DeferredDiagnosticHandler diagnostics = log.new DeferredDiagnosticHandler();
        try {
            JavacParser parser = parsers.newParser(source, false, false, false);
            T tree = production.apply(parser);
            boolean consumedAll = parser.token().kind == TokenKind.EOF;
            return diagnostics.getDiagnostics().isEmpty() && consumedAll ? Optional.of(tree) : Optional.empty();
        } finally {
            log.popDiagnosticHandler(diagnostics);
        }
    }
}
