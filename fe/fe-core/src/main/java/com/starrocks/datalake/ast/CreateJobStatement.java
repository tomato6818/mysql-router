package com.starrocks.datalake.ast;

public class CreateJobStatement extends SystemStatementBase{
    String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
