package com.starrocks.datalake.execute;

import com.starrocks.mysql.MysqlCommand;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface DatalakeExecutor {
    void execute(MysqlCommand command, String query, ByteBuffer packetBuf)throws IOException;
}
