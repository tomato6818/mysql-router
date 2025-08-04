package com.starrocks.datalake.parser;

import com.starrocks.datalake.ast.SystemNode;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DatalakeSystemParser {
    public SystemNode parse(String query) throws IOException {
        InputStream stream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        CharStream charStream = CharStreams.fromStream(stream, StandardCharsets.UTF_8);

        SystemLexer lexer = new SystemLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SystemParser parser = new SystemParser(tokens);

        parser.removeErrorListeners();
        parser.setErrorHandler(new DefaultErrorStrategy());

        System.out.println("--- Parsing started ---\n");
        SystemNode result = null;
        try {
            SystemParser.ProgContext tree = parser.prog();
            DatalakeSystemVisitor visitor = new DatalakeSystemVisitor(tokens);
            result = visitor.visit(tree);

            System.out.println(result);

        } catch (ParseCancellationException e) {
            System.err.println("Error parsing input: " + e.getMessage());
        }

        System.out.println("--- Parsing finished ---");
        return result;
    }
}
