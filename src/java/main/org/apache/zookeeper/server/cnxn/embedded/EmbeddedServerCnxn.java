package org.apache.zookeeper.server.cnxn.embedded;

import org.apache.jute.Record;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.proto.ReplyHeader;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.cnxn.ServerCnxn;
import org.apache.zookeeper.server.ServerStats;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;

public class EmbeddedServerCnxn extends ServerCnxn {

    private Certificate[] NO_CERTIFICATES = new Certificate[0];
    private volatile ZooKeeperServer zkServer;
    private long sesseionId;
    private EmbeddedServerCnxnFactory factory;

    EmbeddedServerCnxn(EmbeddedServerCnxnFactory embeddedServerCnxnFactory) {
        factory = embeddedServerCnxnFactory;
        factory.getCnxns().add(this);
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
    public int getSessionTimeout() {
        return 0;
    }

    @Override
    public void sendResponse(ReplyHeader h, Record r, String tag) throws IOException {

    }

    @Override
    public void sendCloseSession() {

    }

    @Override
    public void sendBuffer(ByteBuffer closeConn) {

    }

    @Override
    public void process(WatchedEvent event) {

    }

    @Override
    public long getSessionId() {
        return this.sesseionId;
    }

    @Override
    public void setSessionId(long sessionId) {
        this.sesseionId = sessionId;
    }

    @Override
    public long getOutstandingRequests() {
        throw new NotImplementedException();
    }

    @Override
    public InetSocketAddress getRemoteSocketAddress() {
        return ECONST.LOCALHOST;
    }

    @Override
    public ServerStats serverStats() {
        if (zkServer != null) {
            return zkServer.serverStats();
        }
        return null;
    }

    @Override
    public int getInterestOps() {
        return -1;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public Certificate[] getClientCertificateChain() {
        return NO_CERTIFICATES;
    }

    @Override
    public void setClientCertificateChain(Certificate[] chain) {

    }

    @Override
    public void close() {

    }
}
