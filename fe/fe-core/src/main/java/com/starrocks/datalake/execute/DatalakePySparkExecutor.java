package com.starrocks.datalake.execute;

import com.starrocks.mysql.MysqlCommand;
import com.starrocks.qe.ConnectContext;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DatalakePySparkExecutor implements DatalakeExecutor {
    ConnectContext ctx;

    public DatalakePySparkExecutor(ConnectContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void execute(MysqlCommand command, String query, ByteBuffer packetBuf) throws IOException {
        ctx.getMysqlChannel().realNetSend(ctx.error(1045, "28000", "%pyspark command not support"));
    }
}
