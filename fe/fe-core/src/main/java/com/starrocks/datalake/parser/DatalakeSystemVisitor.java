package com.starrocks.datalake.parser;


import com.starrocks.datalake.ast.*;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.ArrayList;
import java.util.List;

class DatalakeSystemVisitor extends SystemBaseVisitor<SystemNode> {
    private final CommonTokenStream tokens;

    public DatalakeSystemVisitor(CommonTokenStream tokens) {
        this.tokens = tokens;
    }

    @Override
    public SystemNode visitCreate_starrocks_statement(SystemParser.Create_starrocks_statementContext ctx) {
        StringBuilder print = new StringBuilder(">>> CREATE STARROCKS statement detected:\n");
        print.append("  Cluster Name: ").append(ctx.ID().getText()).append("\n");

        CreateStarrocksStatement result = new CreateStarrocksStatement();
        result.setName(ctx.ID().getText());
        if (ctx.size_spec() != null) {
            result.setSize(ctx.size_spec().ID().getText());
            print.append("  Size: ").append(ctx.size_spec().ID().getText()).append("\n");
        } else if (ctx.resource_spec() != null) {
            result.setFeSpec(tokens.getText(ctx.resource_spec().spec_list(0)));
            result.setCnSpec(tokens.getText(ctx.resource_spec().spec_list(1)));
            print.append("  FE Specs: ")
                    .append(ctx.resource_spec().FE().getText())
                    .append(ctx.resource_spec().LPAREN(0).getText())
                    .append(tokens.getText(ctx.resource_spec().spec_list(0)))
                    .append(ctx.resource_spec().RPAREN(0).getText())
                    .append("\n");

            print.append("  CN Specs: ")
                    .append(ctx.resource_spec().CN().getText())
                    .append(ctx.resource_spec().LPAREN(1).getText())
                    .append(tokens.getText(ctx.resource_spec().spec_list(1)))
                    .append(ctx.resource_spec().RPAREN(1).getText())
                    .append("\n");
        }
        return result;
    }

    @Override
    public SystemNode visitCreate_job_statement(SystemParser.Create_job_statementContext ctx) {
        CreateJobStatement result = new CreateJobStatement();
        result.setName(ctx.ID().getText());
        return result;
    }

    @Override
    public SystemNode visitInsert_job_statement(SystemParser.Insert_job_statementContext ctx) {
        InsertJobStatement result = new InsertJobStatement();
        result.setName(ctx.ID(0).getText());
        result.setTaskName(ctx.NAME().getText());
        result.setQuery(tokens.getText(ctx.query_content()));
        return result;
    }

    @Override
    public SystemNode visitCreate_schedule_statement(SystemParser.Create_schedule_statementContext ctx) {
        CreateScheduleStatement result = new CreateScheduleStatement();
        result.setName(ctx.ID().getText());
        result.setParameters(tokens.getText(ctx.param_list()));
        return result;
    }

    @Override
    public SystemNode visitSelect_statement(SystemParser.Select_statementContext ctx) {
        SelectStatement result = new SelectStatement();

        if (ctx.ID() != null) {
            result.setTable(ctx.ID().getText());

        }

        return result;
    }


    // 최상위 노드에서 모든 결과를 모아서 하나의 문자열로 반환합니다.
    @Override
    public SystemNode visitProg(SystemParser.ProgContext ctx) {
        ProgramStatement result = new ProgramStatement();
        for (SystemParser.StatementContext statementCtx : ctx.statement()) {
            SystemNode statementNode = visit(statementCtx);
            if (statementNode != null) {
                result.getList().add(statementNode);
            }
        }
        return result;
    }




}
