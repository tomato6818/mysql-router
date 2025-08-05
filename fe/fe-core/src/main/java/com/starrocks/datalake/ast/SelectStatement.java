package com.starrocks.datalake.ast;

public class SelectStatement extends SystemStatementBase{
    String table;

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }
}
