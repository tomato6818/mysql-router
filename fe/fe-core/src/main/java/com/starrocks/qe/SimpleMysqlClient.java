package com.starrocks.qe;


import com.starrocks.mysql.MysqlCodec;
import com.starrocks.mysql.MysqlPassword;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class SimpleMysqlClient {
    public static void main(String[] args) throws Exception {
        String host = "10.233.64.149";
        int port = 9030;

        String username = "root";
        String password = "1234";

        try (Socket socket = new Socket(host, port)) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Read handshake packet
            byte[] header = new byte[4];
            in.read(header);
            int payloadLength = ((header[0] & 0xff) | ((header[1] & 0xff) << 8) | ((header[2] & 0xff) << 16));
            int sequenceId = header[3] & 0xff;
            System.out.println("[Handshake] Length: " + payloadLength + ", Seq: " + sequenceId);

            ByteBuffer byteBuffer = ByteBuffer.allocate(payloadLength); // 초기 크기 설정

            byte[] payload = new byte[payloadLength];
            in.read(payload);
            System.out.println("[Handshake] Protocol Version: " + (payload[0] & 0xff));

            byteBuffer.put(payload);
            byteBuffer.flip();
            int protocolVersion = MysqlCodec.readInt1(byteBuffer);
            byte[] mysqlServerVersion = MysqlCodec.readNulTerminateString(byteBuffer);
            int connectionId = MysqlCodec.readInt4(byteBuffer);
            byte[] authPluginData1 = MysqlCodec.readFixedString(byteBuffer,8);
            int i1=MysqlCodec.readInt1(byteBuffer);
            int i2=MysqlCodec.readInt2(byteBuffer);
            int i3=MysqlCodec.readInt1(byteBuffer);
            int i4=MysqlCodec.readInt2(byteBuffer);
            MysqlCodec.readInt2(byteBuffer);
            int authPluginDataLength = MysqlCodec.readInt1(byteBuffer);
            MysqlCodec.readFixedString(byteBuffer,10);
            byte[] authPluginData2 = MysqlCodec.readFixedString(byteBuffer,12);
            int i5=MysqlCodec.readInt1(byteBuffer);
            byte[] pluginname = MysqlCodec.readNulTerminateString(byteBuffer);

            System.out.println("mysqlServerVersion:"+new String(mysqlServerVersion));
            System.out.println("pos:"+byteBuffer.position());
            System.out.println("authPluginData1:"+Arrays.toString(authPluginData1));
            System.out.println("authPluginData2:"+Arrays.toString(authPluginData2));
            System.out.println("pluginname:"+new String(pluginname));




            ByteBuffer passwordBuffer = ByteBuffer.allocate(authPluginData1.length + authPluginData2.length);
            passwordBuffer.put(authPluginData1);
            passwordBuffer.put(authPluginData2);
            byte[] pwBytes = passwordBuffer.array();

            byte[] scramble = MysqlPassword.scramble(pwBytes,password);
            // Send login request
            byte[] usernameBytes = username.getBytes();
            byte[] passwordBytes = password.getBytes();

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Client capability flags (4 bytes)
            buffer.putInt(0x0000a207); // basic flags
            buffer.putInt(16777216); // max packet size
            buffer.put((byte) 33); // character set (utf8_general_ci)
            buffer.position(buffer.position() + 23); // filler

            buffer.put(usernameBytes);
            buffer.put((byte) 0x00); // null-term username

            buffer.put((byte) scramble.length);
            buffer.put(scramble); // password (not hashed)

            // No database
            buffer.put((byte) 0x00);

            int loginPayloadLen = buffer.position();
            byte[] loginHeader = new byte[4];
            loginHeader[0] = (byte) (loginPayloadLen & 0xff);
            loginHeader[1] = (byte) ((loginPayloadLen >> 8) & 0xff);
            loginHeader[2] = (byte) ((loginPayloadLen >> 16) & 0xff);
            loginHeader[3] = (byte) 1; // sequence id

            out.write(loginHeader);
            out.write(buffer.array(), 0, loginPayloadLen);
            out.flush();

            // Read response
            in.read(header);
            int respLen = ((header[0] & 0xff) | ((header[1] & 0xff) << 8) | ((header[2] & 0xff) << 16));
            int respSeq = header[3] & 0xff;
            byte[] respPayload = new byte[respLen];
            in.read(respPayload);

            int status = respPayload[0] & 0xff;
            if (status == 0x00) {
                System.out.println("[OK] Login succeeded.");
            } else if (status == 0xff) {
                System.out.println("[ERR] Login failed. errno: " + ((respPayload[1] & 0xff) | ((respPayload[2] & 0xff) << 8)));
            } else {
                System.out.println("[?] Unknown response: " + status);
            }
        }
    }
}