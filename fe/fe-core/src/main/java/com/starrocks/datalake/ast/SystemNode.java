package com.starrocks.datalake.ast;

import com.starrocks.analysis.Analyzer;
import com.starrocks.common.AnalysisException;
import com.starrocks.common.StarRocksException;
import com.starrocks.sql.ast.AstVisitor;
import com.starrocks.sql.parser.NodePosition;

import java.util.List;

public interface SystemNode {
    /**
     * Perform semantic analysis of node and all of its children.
     * Throws exception if any errors found.
     *
     * @param analyzer
     * @throws AnalysisException, InternalException
     */
    default void analyze(Analyzer analyzer) throws StarRocksException {
        throw new RuntimeException("New AST not support analyze function");
    }

    /**
     * @return SQL syntax corresponding to this node.
     */
    default String toSql() {
        throw new RuntimeException("New AST not implement toSql function");
    }

    NodePosition getPos();

    default <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        throw new RuntimeException("Not implement accept function");
    }

    default List<SystemNode> getList() {
        throw new RuntimeException("Not implement getList function");
    };
}
