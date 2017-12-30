package org.apache.zookeeper.server.cnxn.embedded;

import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.cnxn.ServerCnxn;
import org.apache.zookeeper.server.cnxn.ServerCnxnFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;

import static org.apache.zookeeper.server.cnxn.embedded.ECONST.LOCALHOST;

public class EmbeddedServerCnxnFactory extends ServerCnxnFactory {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedServerCnxnFactory.class);

    private InetSocketAddress localAddress = ECONST.LOCALHOST;
    private int maxClientCnxn = 60;

    @Override
    public int getLocalPort() {
        return LOCALHOST.getPort();
    }

    @Override
    public Iterable<ServerCnxn> getConnections() {
        return cnxns;
    }

    /* only contains local connect, so always return false*/
    @Override
    public boolean closeSession(long sessionId) {
        return false;
    }

    /* the same as before */
    @Override
    public void configure(InetSocketAddress addr, int maxcc, boolean secure) throws IOException {
        localAddress = addr;
        this.maxClientCnxn = maxcc;
    }

    /* the same as before */
    @Override
    public void reconfigure(InetSocketAddress addr) {
        return;
    }

    /* the same as before*/
    @Override
    public int getMaxClientCnxnsPerHost() {
        return 1;
    }

    @Override
    public void setMaxClientCnxnsPerHost(int max) {
        return;
    }

    @Override
    public void startup(ZooKeeperServer zks, boolean startServer) throws IOException, InterruptedException {
        start();
        setZooKeeperServer(zks);
        startServer = false;
        if (startServer) {
            zks.startdata();
            zks.startup();
        }
    }

    /* the method like thread.join*/
    @Override
    public void join() throws InterruptedException {
        throw new NotImplementedException();
    }

    @Override
    public void shutdown() {
        throw new NotImplementedException();
    }

    @Override
    public void start() {
        throw new NotImplementedException();
    }

    @Override
    public void closeAll() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("close local cnxn");
        }
        // clear all the connections on which we are selecting
        int length = cnxns.size();
        for (ServerCnxn cnxn : cnxns) {
            try {
                // This will remove the cnxn from cnxns
                cnxn.close();
            } catch (Exception e) {
                LOG.warn("Ignoring exception closing cnxn sessionid 0x"
                        + Long.toHexString(cnxn.getSessionId()), e);
            }
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public void resetAllConnectionStats() {

    }

    @Override
    public Iterable<Map<String, Object>> getAllConnectionInfo(boolean brief) {
        HashSet<Map<String,Object>> info = new HashSet<Map<String,Object>>();
        // No need to synchronize since cnxns is backed by a ConcurrentHashMap
        for (ServerCnxn c : cnxns) {
            info.add(c.getConnectionInfo(brief));
        }
        return info;
    }
}