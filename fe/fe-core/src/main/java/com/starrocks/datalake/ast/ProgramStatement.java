package com.starrocks.datalake.ast;

import java.util.ArrayList;
import java.util.List;

public class ProgramStatement extends  SystemStatementBase{
    List<SystemNode> list = new ArrayList<SystemNode>();

    public List<SystemNode> getList() {
        return list;
    }

    public void setList(List<SystemNode> list) {
        this.list = list;
    }
}
