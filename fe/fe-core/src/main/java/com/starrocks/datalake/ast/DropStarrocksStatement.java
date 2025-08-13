package com.starrocks.datalake.ast;

public class DropStarrocksStatement extends SystemStatementBase{
    String name;

    public DropStarrocksStatement(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
