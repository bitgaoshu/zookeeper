/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.server.cnxn;

import org.apache.jute.Record;
import org.apache.zookeeper.server.statistics.ServerStats;
import org.apache.zookeeper.watcher.WatchedEvent;
import org.apache.zookeeper.proto.ReplyHeader;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;

public class MockServerCnxn extends ServerCnxn {
    public Certificate[] clientChain;
    public boolean secure;

    @Override
    public int getSessionTimeout() {
        return 0;
    }

    @Override
    public void close() {
    }

    @Override
    public void sendResponse(ReplyHeader h, Record r, String tag)
            throws IOException {
    }

    @Override
    public void sendCloseSession() {
    }

    @Override
    public void process(WatchedEvent event) {
    }

    @Override
    public long getSessionId() {
        return 0;
    }

    @Override
    public void setSessionId(long sessionId) {
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public Certificate[] getClientCertificateChain() {
        return clientChain;
    }

    @Override
    public void setClientCertificateChain(Certificate[] chain) {
        clientChain = chain;
    }

    @Override
    public void sendBuffer(ByteBuffer closeConn) {
    }

    @Override
    public void enableRecv() {
    }

    @Override
    public void disableRecv() {
    }

    @Override
    public void setSessionTimeout(int sessionTimeout) {
    }

    @Override
    protected ServerStats serverStats() {
        return null;
    }

    @Override
    public long getOutstandingRequests() {
        return 0;
    }

    @Override
    public InetSocketAddress getRemoteSocketAddress() {
        return null;
    }

    @Override
    public int getInterestOps() {
        return 0;
    }
}