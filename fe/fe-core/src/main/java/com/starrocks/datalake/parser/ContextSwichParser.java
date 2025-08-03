package com.starrocks.datalake.parser;

import com.starrocks.datalake.ContextBlockInfo;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ContextSwichParser {

    public String getCurrentContext() {
        return currentContext;
    }

    private String currentContext = "STARROCKS";

    // ------------------------------------------------------------------
    // Listener: 파싱 과정을 '듣고' 결과를 저장하는 객체
    // ------------------------------------------------------------------

    static class ContextBlockListener extends ContextBaseListener {
        private final CommonTokenStream tokens;
        private ContextBlockInfo blockInfo;
        private String contextFromDeclaration = null;

        public ContextBlockListener(CommonTokenStream tokens) {
            this.tokens = tokens;
        }

        // 컨텍스트 선언 규칙에 진입할 때 호출
        @Override
        public void enterContext_declaration_statement(ContextParser.Context_declaration_statementContext ctx) {
            String declaration = ctx.CONTEXT_TOKEN().getText();
            System.out.println("enterContext_declaration_statement declaration:"+declaration);
            contextFromDeclaration = declaration.substring(1).toUpperCase();
        }

        // 쿼리 문장 규칙에서 빠져나올 때 호출
        @Override
        public void exitQuery_statement(ContextParser.Query_statementContext ctx) {
            int startTokenIndex = ctx.getStart().getTokenIndex();
            int endTokenIndex = ctx.getStop().getTokenIndex();
            String queryText = tokens.getText(new Interval(startTokenIndex, endTokenIndex));
            System.out.println("exitQuery_statement queryText:"+queryText);
            this.blockInfo = new ContextBlockInfo(null, startTokenIndex, endTokenIndex, queryText);
        }

        public ContextBlockInfo getBlockInfo() {
            return blockInfo;
        }

        public String getContextFromDeclaration() {
            return contextFromDeclaration;
        }
    }

    // ------------------------------------------------------------------
    // 상태를 관리하는 public 파서 메서드
    // ------------------------------------------------------------------

    public ContextBlockInfo parse(String statement) throws IOException {
        CommonTokenStream tokens = createTokenStream(statement+";");
        ContextParser parser = new ContextParser(tokens);
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());

        ParseTree tree = parser.file();

        // Listener를 생성하고 파서에게 파싱 결과를 전달하도록 지시
        ParseTreeWalker walker = new ParseTreeWalker();
        ContextBlockListener listener = new ContextBlockListener(tokens);
        walker.walk(listener, tree);

        System.out.println("blockInfo:" + listener.getBlockInfo()); // Listener에서 직접 결과를 가져옵니다.

        // Listener에서 얻은 정보로 상태를 업데이트하거나 쿼리 블록을 반환합니다.
        if (listener.getContextFromDeclaration() != null) {
            this.currentContext = listener.getContextFromDeclaration();
            return null;
        }

        ContextBlockInfo blockInfo = listener.getBlockInfo();
        if (blockInfo != null) {
            blockInfo.setType(this.currentContext);
        }

        return blockInfo;
    }

    private CommonTokenStream createTokenStream(String input) throws IOException {
        InputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        CharStream charStream = CharStreams.fromStream(stream, StandardCharsets.UTF_8);
        return new CommonTokenStream(new ContextLexer(charStream));
    }

    public static void main(String[] args) throws IOException {
        ContextSwichParser singleStatementParser = new ContextSwichParser();

        String[] statements = {
                "%system  ;  ",
                "select * from T1;",
                "select * from T2;",
                "%pyspark;",
                "print('Hello World');",
                "%starrocks;",
                "select * from T3;"
        };

        System.out.println("--- 순차적 파싱 시작 ---");
        for (String statement : statements) {
            System.out.println("입력 문장: \"" + statement.trim() + "\"");
            ContextBlockInfo result = singleStatementParser.parse(statement);

            if (result != null) {
                System.out.println("  -> 감지된 컨텍스트: " + result.getType());
                System.out.println("  -> 쿼리 내용: " + result.getQuery().trim() + "\n");
            } else {
                System.out.println("  -> 컨텍스트 변경만 감지됨: " + singleStatementParser.currentContext + "\n");
            }
        }
    }
}
