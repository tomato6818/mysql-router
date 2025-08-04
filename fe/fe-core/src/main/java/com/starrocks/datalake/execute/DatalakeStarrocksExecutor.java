package com.starrocks.datalake.execute;

import com.starrocks.mysql.MysqlCommand;
import com.starrocks.qe.ConnectContext;
import com.starrocks.sql.ast.StatementBase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class DatalakeStarrocksExecutor implements DatalakeExecutor {
    ConnectContext ctx;
    public DatalakeStarrocksExecutor(ConnectContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void execute(MysqlCommand command, String query, ByteBuffer packetBuf) throws IOException {
        StatementBase parsedStmt = null;
        List<StatementBase> stmts = null;
        Class parsedStmtClass = null;

        if (command == MysqlCommand.COM_QUERY) {
            stmts = com.starrocks.sql.parser.SqlParser.parse(query, ctx.getSessionVariable());
            for (int i = 0; i < stmts.size(); ++i) {
                parsedStmt = stmts.get(i);
            }
            if (parsedStmt != null) {
                parsedStmtClass = parsedStmt.getClass();
            }
        }

        System.out.println("command:"+command+" parsedStmtClass:" + parsedStmtClass);
        ctx.getMysqlChannel().realNetSend(ctx.proxy(packetBuf, command, parsedStmtClass));
    }
}
