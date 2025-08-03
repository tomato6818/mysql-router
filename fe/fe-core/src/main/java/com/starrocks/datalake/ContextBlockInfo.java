package com.starrocks.datalake;

public class ContextBlockInfo {
    String type;
    int startTokenIndex;
    int endTokenIndex;
    String query;

    public ContextBlockInfo(String type, int startTokenIndex, int endTokenIndex, String query) {
        this.type = type;
        this.startTokenIndex = startTokenIndex;
        this.endTokenIndex = endTokenIndex;
        this.query = query;
    }

    public int getStartTokenIndex() {
        return startTokenIndex;
    }

    public void setStartTokenIndex(int startTokenIndex) {
        this.startTokenIndex = startTokenIndex;
    }

    public int getEndTokenIndex() {
        return endTokenIndex;
    }

    public void setEndTokenIndex(int endTokenIndex) {
        this.endTokenIndex = endTokenIndex;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}