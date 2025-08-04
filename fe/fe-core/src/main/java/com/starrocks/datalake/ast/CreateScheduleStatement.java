package com.starrocks.datalake.ast;

public class CreateScheduleStatement extends SystemStatementBase{
    String name;
    String parameters;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
}
