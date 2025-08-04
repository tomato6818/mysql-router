package com.starrocks.mysql;

import com.starrocks.qe.ConnectContext;
import com.starrocks.sql.ast.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MysqlProxy {
    boolean isLogin;
    int sequenceId;
    int sendSequenceId;
    Socket socket;
    String host = "10.233.64.26";
    int port = 9030;

    String username = "root";
    String password = "1234";
    MysqlSerializer mysqlSerializer;
    public MysqlProxy() {
        this.isLogin = false;
        this.mysqlSerializer = MysqlSerializer.newInstance();
    }

    public ByteBuffer send(ByteBuffer byteBuffer, MysqlCommand command, Class stmtClass) {
        List<byte[]> responsePackets = new ArrayList<>();

        int length = byteBuffer.limit();
        System.out.println("MysqlProxy Send byteBuffer length:" + length);
        if(socket == null || !socket.isConnected()) {
            System.out.println("MysqlProxy Send socket null");
            return null;
        }

        int ii=0;
        while (byteBuffer.hasRemaining()) {
            System.out.print((char) byteBuffer.get()); // 출력: abc
            ii++;
        }
        System.out.println("");
        System.out.println("ii:"+ii);

        byteBuffer.rewind();
        try {

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            System.out.println("MysqlProxy Send Sending");
            //writeHeader(length, out);
            //writeBuffer(byteBuffer, out);
            sendSequenceId = 0;
            sendPacket(byteBuffer, out);
            //accSequenceId();
            //out.flush();
            System.out.println("MysqlProxy Send Sended");

            // 5. Read Column Count
            byte[] columnCountPacket = readPacket(in,responsePackets);

            int firstByte = columnCountPacket[0] & 0xFF;
            if (firstByte == 0xFF) {
                handleErrorPacket(columnCountPacket);
                return mergePacketsToByteBuffer(responsePackets); // 또는 적절한 에러 처리
            }

            if (stmtClass == InsertStmt.class
                    || stmtClass == UpdateStmt.class
                    || stmtClass == DeleteStmt.class
                    || stmtClass == CreateDbStmt.class
                    || stmtClass == CreateTableStmt.class) {
                return createOkPacket(
                        1,       // sequenceId
                        1L,      // affectedRows
                        0L,      // lastInsertId
                        2,       // serverStatus (autocommit)
                        0        // warningCount
                );
            }

            if (command == MysqlCommand.COM_INIT_DB) {
                return createOkPacket(
                        1,       // sequenceId
                        1L,      // affectedRows
                        0L,      // lastInsertId
                        2,       // serverStatus (autocommit)
                        0        // warningCount
                );
            }

            if (command == MysqlCommand.COM_FIELD_LIST) {
                readPacket(in,responsePackets);
                return mergePacketsToByteBuffer(responsePackets);
            }

            int columnCount = firstByte;
            System.out.println("Column count: " + columnCount);

            // 6. Read Column Definition packets
            for (int i = 0; i < columnCount; i++) {
                byte[] colDef = readPacket(in,responsePackets);
                // Column info is optional here for demo
            }

            // 7. EOF packet
            List.of(readPacket(in, responsePackets));

            // 8. Read Row Data packets until EOF
            while (true) {
                byte[] row = readPacket(in, responsePackets);
                if ((row[0] & 0xFF) == 0xFE && row.length < 9) {
                    System.out.println("EOF reached.");
                    break;
                }
                parseRow(row);
            }


        } catch (IOException e) {
            // 연결 종료 or 에러
            e.printStackTrace();
        }


        byteBuffer.rewind();


        return mergePacketsToByteBuffer(responsePackets);
    }



    public int query(String query) {
        System.out.println("MysqlProxy Start query:" + query);
        byte command = 0x03; // COM_QUERY
        byte[] queryBytes = query.getBytes(StandardCharsets.UTF_8);
        int packetSize = 1 + queryBytes.length; // 1 byte for command + query

        ByteBuffer buffer = ByteBuffer.allocate(packetSize);
        buffer.put(command);
        buffer.put(queryBytes);
        buffer.flip(); // ready for reading
        buffer.rewind();

        send(buffer, MysqlCommand.COM_QUERY, null);
        System.out.println("MysqlProxy Fininsh query:" + query);
        return 0;
    }

    public static ByteBuffer mergePacketsToByteBuffer(List<byte[]> responsePackets) {
        // 총 크기 계산
        int totalLength = 0;
        for (byte[] packet : responsePackets) {
            totalLength += packet.length;
        }

        // ByteBuffer 생성
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);

        // 데이터 복사
        for (byte[] packet : responsePackets) {
            buffer.put(packet);
        }

        // 읽기 준비를 위해 flip
        buffer.flip();
        System.out.println("mergePacketsToByteBuffer:"+Arrays.toString(buffer.array()));
        buffer.rewind();
        return buffer;
    }

    private void handleErrorPacket(byte[] packet) {
        int pos = 1; // 0번째는 0xFF
        int errorCode = ((packet[pos++] & 0xFF) | ((packet[pos++] & 0xFF) << 8));

        // SQL state marker
        String sqlState = "";
        if (packet[pos] == '#') {
            pos++;
            byte[] stateBytes = Arrays.copyOfRange(packet, pos, pos + 5);
            sqlState = new String(stateBytes, StandardCharsets.UTF_8);
            pos += 5;
        }

        String errorMessage = new String(packet, pos, packet.length - pos, StandardCharsets.UTF_8);

        System.err.printf("❌ MySQL Error: [%d] SQLState=%s, Message=%s%n", errorCode, sqlState, errorMessage);
    }

    private void sendPacket(ByteBuffer payload, OutputStream out) throws IOException {
        int length = payload.limit();
        byte[] packet = new byte[4 + length]; // header(4) + payload

        // 1. Length (3바이트 Little Endian)
        packet[0] = (byte) (length & 0xFF);
        packet[1] = (byte) ((length >> 8) & 0xFF);
        packet[2] = (byte) ((length >> 16) & 0xFF);

        // 2. Sequence ID (1바이트)
        packet[3] = (byte) (sendSequenceId & 0xFF);

        // 3. Copy payload into packet
        System.arraycopy(payload.array(), 0, packet, 4, length);

        // 4. Write full packet at once
        out.write(packet);
        out.flush();
    }


    private byte[] readPacket(InputStream in, List<byte[]> responsePackets) throws IOException {
        byte[] header = in.readNBytes(4);
        int length = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8) | ((header[2] & 0xFF) << 16);
        sequenceId = header[3] & 0xFF;
        System.out.println("readPacket length:" + length + " sequenceId:" + sequenceId);

        responsePackets.addAll(List.of(header));
        System.out.println("readPacket:"+Arrays.toString(header));
        byte[] body = in.readNBytes(length);

        System.out.println("readPacket:"+Arrays.toString(body));
        responsePackets.addAll(List.of(body));
        return body;
    }



    private void parseRow(byte[] packet) {
        List<String> values = new ArrayList<>();
        int pos = 0;
        while (pos < packet.length) {
            int b = packet[pos] & 0xFF;
            if (b == 0xFB) { // NULL
                values.add("NULL");
                pos += 1;
            } else {
                long len = packet[pos++] & 0xFF;
                if (len >= 0xFC) {
                    // handle length encoded integer (omitted for brevity)
                }
                String val = new String(packet, pos, (int) len);
                values.add(val);
                pos += len;
            }
        }
        System.out.println("Row: " + values);
    }




    private int writeHeader(int length, OutputStream out) throws IOException {
        // 3바이트 Little Endian으로 length 쓰기
        out.write((byte) (length & 0xFF));         // 첫 번째 바이트 (LSB)
        out.write((byte) ((length >> 8) & 0xFF));  // 두 번째 바이트
        out.write((byte) ((length >> 16) & 0xFF)); // 세 번째 바이트
        // 1바이트 sequenceId 쓰기
        out.write((byte) (sendSequenceId & 0xFF));     // 네 번째 바이트
        return 4; // 총 4바이트 썼음을 반환
    }

    private int writeBuffer(ByteBuffer byteBuffer, OutputStream out) {
        try {
            out.write(byteBuffer.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    public boolean getIsLogin() {
        return isLogin;
    }
    public int login() throws Exception {
        System.out.println("MysqlProxy login");
        if(socket != null && socket.isConnected()) {
            return 0;
        }

        socket = new Socket(host, port); // try 밖에서 열기


        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            sequenceId = 0;
            // Read handshake packet
            byte[] header = new byte[4];
            in.read(header);
            int payloadLength = ((header[0] & 0xff) | ((header[1] & 0xff) << 8) | ((header[2] & 0xff) << 16));
            sequenceId = header[3] & 0xff;
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
            System.out.println("authPluginData1:"+ Arrays.toString(authPluginData1));
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
                isLogin = true;
            } else if (status == 0xff) {
                System.out.println("[ERR] Login failed. errno: " + ((respPayload[1] & 0xff) | ((respPayload[2] & 0xff) << 8)));
            } else {
                System.out.println("[?] Unknown response: " + status);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return 0;
    }

    private void accSequenceId() {
        sequenceId++;
        if (sequenceId > 255) {
            sequenceId = 0;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

        /**
         * MySQL OK 패킷을 생성하여 ByteBuffer 객체로 반환합니다.
         * @param sequenceId 패킷 순서 ID
         * @param affectedRows 쿼리에 의해 영향을 받은 행의 수
         * @param lastInsertId 마지막으로 삽입된 행의 ID
         * @param serverStatus 서버 상태 플래그
         * @param warningCount 경고 개수
         * @return OK 패킷 전체를 담은 ByteBuffer (position은 페이로드 끝에 위치함)
         */
        public  ByteBuffer createOkPacket(int sequenceId, long affectedRows, long lastInsertId, int serverStatus, int warningCount) {

            // 페이로드 버퍼를 미리 충분한 크기로 할당합니다.
            // 최대 페이로드 길이는 OK 헤더(1) + 가변길이 8바이트(2) + status(2) + warnings(2) = 21바이트
            ByteBuffer payloadBuffer = ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN);

            // 1. OK 패킷 헤더: 0x00
            payloadBuffer.put((byte) 0x00);

            // 2. Affected Rows (가변 길이)
            writeLengthEncodedInteger(payloadBuffer, affectedRows);

            // 3. Last Insert ID (가변 길이)
            writeLengthEncodedInteger(payloadBuffer, lastInsertId);

            // 4. Status Flags (2바이트, Little Endian)
            payloadBuffer.putShort((short) serverStatus);

            // 5. Warnings (2바이트, Little Endian)
            payloadBuffer.putShort((short) warningCount);

            // 페이로드의 실제 길이를 계산합니다.
            int payloadLength = payloadBuffer.position();

            // 최종 패킷을 담을 버퍼를 생성합니다.
            // 헤더 (3바이트 길이 + 1바이트 시퀀스 ID) + 페이로드
            ByteBuffer finalBuffer = ByteBuffer.allocate(4 + payloadLength).order(ByteOrder.LITTLE_ENDIAN);

            // 1. 패킷 헤더 (3바이트 길이, Little Endian)
            finalBuffer.put((byte) (payloadLength & 0xFF));
            finalBuffer.put((byte) ((payloadLength >> 8) & 0xFF));
            finalBuffer.put((byte) ((payloadLength >> 16) & 0xFF));

            // 2. Sequence ID (1바이트)
            finalBuffer.put((byte) sequenceId);

            // 3. 페이로드
            // payloadBuffer의 position을 0으로 되돌려 처음부터 읽을 수 있도록 준비
            payloadBuffer.flip();
            finalBuffer.put(payloadBuffer);

            // 최종 버퍼의 position을 처음으로 되돌려 읽을 준비
            finalBuffer.flip();

            return finalBuffer;
        }

        /**
         * 가변 길이 정수를 ByteBuffer에 씁니다.
         * @param buffer 데이터를 쓸 버퍼
         * @param value 쓸 값
         */
        private void writeLengthEncodedInteger(ByteBuffer buffer, long value) {
            if (value < 251) {
                buffer.put((byte) value);
            } else if (value < 65536) { // 2^16
                buffer.put((byte) 0xFC);
                buffer.putShort((short) value);
            } else if (value < 16777216) { // 2^24
                buffer.put((byte) 0xFD);
                buffer.put((byte) (value & 0xFF));
                buffer.put((byte) ((value >> 8) & 0xFF));
                buffer.put((byte) ((value >> 16) & 0xFF));
            } else {
                buffer.put((byte) 0xFE);
                buffer.putLong(value);
            }
        }

    public ByteBuffer createErrorPacket(int sequenceId, int errorCode, String sqlState, String errorMessage) {

        // 에러 메시지를 바이트로 변환
        byte[] messageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);

        // 페이로드 길이 계산
        // 1(ERR 헤더) + 2(Error Code) + 1(SQL State 마커) + 5(SQL State) + 메시지 길이
        int payloadLength = 1 + 2 + 1 + 5 + messageBytes.length;

        // 최종 패킷을 담을 버퍼를 생성 (헤더 4바이트 + 페이로드)
        ByteBuffer finalBuffer = ByteBuffer.allocate(4 + payloadLength).order(ByteOrder.LITTLE_ENDIAN);

        // 1. 패킷 헤더 (3바이트 길이)
        finalBuffer.put((byte) (payloadLength & 0xFF));
        finalBuffer.put((byte) ((payloadLength >> 8) & 0xFF));
        finalBuffer.put((byte) ((payloadLength >> 16) & 0xFF));

        // 2. Sequence ID (1바이트)
        finalBuffer.put((byte) sequenceId);

        // 3. 페이로드
        // 3-1. ERR 패킷 헤더 (0xFF)
        finalBuffer.put((byte) 0xFF);

        // 3-2. Error Code (2바이트, Little Endian)
        finalBuffer.putShort((short) errorCode);

        // 3-3. SQL State 마커 ('#')
        finalBuffer.put((byte) '#');

        // 3-4. SQL State (5바이트)
        finalBuffer.put(sqlState.getBytes(StandardCharsets.US_ASCII));

        // 3-5. Error Message
        finalBuffer.put(messageBytes);

        // 버퍼를 읽기 모드로 전환
        finalBuffer.flip();

        return finalBuffer;
    }

}
