package com.starrocks.datalake.parser;


import org.antlr.v4.runtime.CommonTokenStream;

class DatalakeSystemVisitor extends SystemBaseVisitor<String> {
    private final CommonTokenStream tokens;

    public DatalakeSystemVisitor(CommonTokenStream tokens) {
        this.tokens = tokens;
    }

    @Override
    public String visitCreate_starrocks_statement(SystemParser.Create_starrocks_statementContext ctx) {
        StringBuilder result = new StringBuilder(">>> CREATE STARROCKS statement detected:\n");
        result.append("  Cluster Name: ").append(ctx.ID().getText()).append("\n");

        if (ctx.size_spec() != null) {
            result.append("  Size: ").append(ctx.size_spec().ID().getText()).append("\n");
        } else if (ctx.resource_spec() != null) {
            result.append("  FE Specs: ")
                    .append(ctx.resource_spec().FE().getText())
                    .append(ctx.resource_spec().LPAREN(0).getText())
                    .append(tokens.getText(ctx.resource_spec().spec_list(0)))
                    .append(ctx.resource_spec().RPAREN(0).getText())
                    .append("\n");

            result.append("  CN Specs: ")
                    .append(ctx.resource_spec().CN().getText())
                    .append(ctx.resource_spec().LPAREN(1).getText())
                    .append(tokens.getText(ctx.resource_spec().spec_list(1)))
                    .append(ctx.resource_spec().RPAREN(1).getText())
                    .append("\n");
        }
        return result.toString();
    }

    @Override
    public String visitCreate_job_statement(SystemParser.Create_job_statementContext ctx) {
        return ">>> CREATE JOB statement detected:\n  Job Name: " + ctx.ID().getText() + "\n";
    }

    @Override
    public String visitInsert_job_statement(SystemParser.Insert_job_statementContext ctx) {
        StringBuilder result = new StringBuilder(">>> INSERT JOB statement detected:\n");
        result.append("  Job Name: ").append(ctx.ID(0).getText()).append("\n");
        result.append("  Task Name: ").append(ctx.NAME().getText()).append("\n");
        result.append("  Query: ").append(tokens.getText(ctx.query_content())).append("\n");
        return result.toString();
    }

    @Override
    public String visitCreate_schedule_statement(SystemParser.Create_schedule_statementContext ctx) {
        StringBuilder result = new StringBuilder(">>> CREATE SCHEDULE statement detected:\n");
        result.append("  Schedule Name: ").append(ctx.ID().getText()).append("\n");
        result.append("  Parameters: ").append(tokens.getText(ctx.param_list())).append("\n");
        return result.toString();
    }

    // 최상위 노드에서 모든 결과를 모아서 하나의 문자열로 반환합니다.
    @Override
    public String visitProg(SystemParser.ProgContext ctx) {
        StringBuilder result = new StringBuilder();
        for (SystemParser.StatementContext statementCtx : ctx.statement()) {
            result.append(visit(statementCtx)).append("\n");
        }
        return result.toString();
    }
}
