package com.starrocks.datalake.ast;

import com.starrocks.sql.parser.NodePosition;

public class SystemStatementBase implements SystemNode{
    @Override
    public NodePosition getPos() {
        return null;
    }
}
