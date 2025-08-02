// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/mysql/MysqlAuthPacket.java

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.mysql;

import com.google.common.collect.Maps;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// MySQL protocol handshake response packet, which contain authenticate information.
public class MysqlAuthPacket extends MysqlPacket {
    private int maxPacketSize;
    private int characterSet;
    private String userName;
    private byte[] authResponse;
    private String database;
    private String pluginName;
    private MysqlCapability capability;
    private Map<String, String> connectAttributes;

    public String getUser() {
        return userName;
    }

    public byte[] getAuthResponse() {
        return authResponse;
    }

    public void setAuthResponse(byte[] bytes) {
        authResponse = bytes;
    }

    public String getDb() {
        return database;
    }

    public MysqlCapability getCapability() {
        return capability;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public boolean isSSLConnRequest() {
        return capability.isSSL();
    }

    public Map<String, String> getConnectAttributes() {
        return connectAttributes;
    }

    @Override
    public boolean readFrom(ByteBuffer buffer) {
        System.out.println("readFrom");
        // read capability four byte, which CLIENT_PROTOCOL_41 must be set
        capability = new MysqlCapability(MysqlCodec.readInt4(buffer));
        if (!capability.isProtocol41()) {
            return false;
        }
        System.out.println("readFrom capability:"+capability);
        // max packet size
        maxPacketSize = MysqlCodec.readInt4(buffer);

        System.out.println("readFrom maxPacketSize:"+maxPacketSize);
        // character set. only support 33(utf-8)?
        characterSet = MysqlCodec.readInt1(buffer);

        System.out.println("readFrom characterSet:"+characterSet);
        // reserved 23 bytes
        buffer.position(buffer.position() + 23);

        // if the request is a ssl request, the package is truncated here.
        if (buffer.remaining() <= 0 && capability.isSSL()) {
            return true;
        }
        // user name
        userName = new String(MysqlCodec.readNulTerminateString(buffer));
        System.out.println("readFrom userName:"+userName);
        if (capability.isPluginAuthDataLengthEncoded()) {
            authResponse = MysqlCodec.readLenEncodedString(buffer);
            System.out.println("readFrom authResponse 1:"+authResponse);
        } else if (capability.isSecureConnection()) {
            int len = MysqlCodec.readInt1(buffer);
            authResponse = MysqlCodec.readFixedString(buffer, len);
            System.out.println("readFrom authResponse 2:"+authResponse);
        } else {
            authResponse = MysqlCodec.readNulTerminateString(buffer);
            System.out.println("readFrom authResponse 3:"+authResponse);
        }
        // DB to use
        if (buffer.remaining() > 0 && capability.isConnectedWithDb()) {
            database = new String(MysqlCodec.readNulTerminateString(buffer));
            System.out.println("readFrom database:"+database);
        }
        // plugin name to plugin
        if (buffer.remaining() > 0 && capability.isPluginAuth()) {
            pluginName = new String(MysqlCodec.readNulTerminateString(buffer));
            System.out.println("readFrom pluginName:"+pluginName);
        }
        // connect attrs
        if (buffer.remaining() > 0 && capability.isConnectAttrs()) {
            connectAttributes = parseConnectAttrs(buffer);
            System.out.println("readFrom connectAttributes:"+connectAttributes);
        }

        // Commented for JDBC
        // if (buffer.remaining() != 0) {
        //     return false;
        // }
        return true;
    }

    private Map<String, String> parseConnectAttrs(ByteBuffer buffer) {
        String key = "";
        String value = "";
        connectAttributes = Maps.newHashMap();
        try {
            long allAttrLength = MysqlCodec.readVInt(buffer);
            long curDealLen = 0;
            while (buffer.remaining() > 0 && allAttrLength - curDealLen > 0) {
                key = value = "";
                long keyLength = MysqlCodec.readVInt(buffer);

                if (buffer.remaining() >= keyLength) {
                    key = new String(MysqlCodec.readFixedString(buffer, (int) keyLength));
                } else {
                    return connectAttributes;
                }
                curDealLen += keyLength;
                long valLength = MysqlCodec.readVInt(buffer);
                if (buffer.remaining() >= valLength) {
                    value = new String(MysqlCodec.readFixedString(buffer, (int) valLength));
                } else {
                    // only parse key success
                    connectAttributes.put(key, "");
                    return connectAttributes;
                }
                curDealLen += valLength;
                connectAttributes.put(key, value);
            }
        } catch (Exception ex) {
            connectAttributes.put(key, value);
        }
        return connectAttributes;
    }

    @Override
    public void writeTo(MysqlSerializer serializer) {

    }

    public static byte[] buildLoginPacket(String user, byte[] scramble, String db) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1. capability flags (CLIENT_PROTOCOL_41 | CLIENT_SECURE_CONNECTION | CLIENT_PLUGIN_AUTH)
        int capabilities = 0x00000200; // CLIENT_PROTOCOL_41만 넣고 시작

        out.write(capabilities & 0xFF);
        out.write((capabilities >> 8) & 0xFF);
        out.write((capabilities >> 16) & 0xFF);
        out.write((capabilities >> 24) & 0xFF);

        // 2. max packet size (4 bytes)
        out.write(new byte[]{(byte) 0x00, 0x00, 0x00, 0x01});

        // 3. charset (1 byte) – utf8_general_ci
        out.write(0x21);

        // 4. reserved (23 bytes)
        out.write(new byte[23]);

        // 5. username (null-terminated)
        out.write(user.getBytes(StandardCharsets.UTF_8));
        out.write(0x00);

        // 6. password (length-prefixed scramble)
        out.write(scramble.length); // 20
        out.write(scramble);

        // 7. database (optional, null-terminated)
        if (db != null && !db.isEmpty()) {
            out.write(db.getBytes(StandardCharsets.UTF_8));
            out.write(0x00);
        }

        // 8. auth plugin name (null-terminated) — "mysql_native_password"
        out.write("mysql_native_password".getBytes(StandardCharsets.UTF_8));
        out.write(0x00);

        return out.toByteArray();
    }

    public static byte[] build(String username, byte[] scrambledPassword, String database) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // client capability flag (4 bytes)
        out.write(new byte[]{(byte) 0x85, (byte) 0xa6, 0x03, 0x00});
        // max packet size (4 bytes)
        out.write(new byte[]{(byte) 0x00, 0x00, 0x00, 0x01});
        // charset (1 byte)
        out.write(0x21);
        // reserved (23 bytes)
        out.write(new byte[23]);

        // username (null-terminated)
        out.write(username.getBytes(StandardCharsets.UTF_8));
        out.write(0x00);

        // password
        out.write((byte) scrambledPassword.length);
        out.write(scrambledPassword);

        // database (null-terminated)
        if (database != null) {
            out.write(database.getBytes(StandardCharsets.UTF_8));
            out.write(0x00);
        }

        return out.toByteArray();
    }
}
