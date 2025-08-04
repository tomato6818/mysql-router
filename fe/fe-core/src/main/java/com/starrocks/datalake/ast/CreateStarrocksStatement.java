package com.starrocks.datalake.ast;

public class CreateStarrocksStatement extends SystemStatementBase{
    String name;
    String size;
    String feSpec;
    String cnSpec;

    public CreateStarrocksStatement() {

    }

    public CreateStarrocksStatement(String name, String size, String feSpec, String cnSpec) {
        this.name = name;
        this.size = size;
        this.feSpec = feSpec;
        this.cnSpec = cnSpec;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getFeSpec() {
        return feSpec;
    }

    public void setFeSpec(String feSpec) {
        this.feSpec = feSpec;
    }

    public String getCnSpec() {
        return cnSpec;
    }

    public void setCnSpec(String cnSpec) {
        this.cnSpec = cnSpec;
    }
}
