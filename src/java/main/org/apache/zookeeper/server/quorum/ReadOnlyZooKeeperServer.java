/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server.quorum;

import java.io.PrintWriter;

import org.apache.zookeeper.server.ZKSState;
import org.apache.zookeeper.server.jmx.MBeanRegistry;
import org.apache.zookeeper.server.jmx.impl.DataTreeBean;
import org.apache.zookeeper.server.processor.FinalRequestProcessor;
import org.apache.zookeeper.server.processor.PrepRequestProcessor;
import org.apache.zookeeper.server.processor.RequestProcessor;
import org.apache.zookeeper.server.persistence.ZKDatabase;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.jmx.impl.ZooKeeperServerBean;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.mBean.impl.LocalPeerBean;
import org.apache.zookeeper.server.quorum.mBean.impl.ReadOnlyBean;
import org.apache.zookeeper.server.quorum.processor.ReadOnlyRequestProcessor;

/**
 * A ZooKeeperServer which comes into play when peer is partitioned from the
 * majority. Handles read-only clients, but drops connections from not-read-only
 * ones.
 * <p>
 * The very first processor in the chain of request processors is a
 * ReadOnlyRequestProcessor which drops state-changing requests.
 */
public class ReadOnlyZooKeeperServer extends ZooKeeperServer {

    protected final QuorumPeer self;
    private volatile boolean shutdown = false;

    ReadOnlyZooKeeperServer(FileTxnSnapLog logFactory, QuorumPeer self,
                            ZKDatabase zkDb) {
        super(logFactory, self.getTickTime(), self.minSessionTimeout,
              self.maxSessionTimeout, zkDb);
        this.self = self;
    }

    @Override
    protected void setupRequestProcessors() {
        RequestProcessor finalProcessor = new FinalRequestProcessor(this);
        RequestProcessor prepProcessor = new PrepRequestProcessor(this, finalProcessor);
        ((PrepRequestProcessor) prepProcessor).start();
        firstProcessor = new ReadOnlyRequestProcessor(this, prepProcessor);
        ((ReadOnlyRequestProcessor) firstProcessor).start();
    }

    @Override
    public synchronized void startup() {
        // check to avoid startup follows shutdown
        if (shutdown) {
            LOG.warn("Not starting Read-only processor as startup follows shutdown!");
            return;
        }
        registerJMX(new ReadOnlyBean(this), self.getJmxLocalPeerBean());
        super.startup();
        self.setZooKeeperServer(this);
        self.adminServer.setZooKeeperServer(this);
        LOG.info("Read-only processor started");
    }

    @Override
    protected void registerJMX() {
        // register with JMX
        try {
            jmxDataTreeBean = new DataTreeBean(getZKDatabase().getDataTree());
            MBeanRegistry.getInstance().register(jmxDataTreeBean, jmxServerBean);
        } catch (Exception e) {
            LOG.warn("Failed to register with JMX", e);
            jmxDataTreeBean = null;
        }
    }

    private void registerJMX(ZooKeeperServerBean serverBean, LocalPeerBean localPeerBean) {
        // register with JMX
        try {
            jmxServerBean = serverBean;
            MBeanRegistry.getInstance().register(serverBean, localPeerBean);
        } catch (Exception e) {
            LOG.warn("Failed to register with JMX", e);
            jmxServerBean = null;
        }
    }

    @Override
    protected void unregisterJMX() {
        // unregister from JMX
        try {
            if (jmxDataTreeBean != null) {
                MBeanRegistry.getInstance().unregister(jmxDataTreeBean);
            }
        } catch (Exception e) {
            LOG.warn("Failed to unregister with JMX", e);
        }
        jmxDataTreeBean = null;
    }

    protected void unregisterJMX(ZooKeeperServer zks) {
        // unregister from JMX
        try {
            if (jmxServerBean != null) {
                MBeanRegistry.getInstance().unregister(jmxServerBean);
            }
        } catch (Exception e) {
            LOG.warn("Failed to unregister with JMX", e);
        }
        jmxServerBean = null;
    }

    @Override
    public String getState() {
        return "read-only";
    }

    /**
     * Returns the id of the associated QuorumPeer, which will do for a unique
     * id of this processor.
     */
    @Override
    public long getServerId() {
        return self.getId();
    }

    @Override
    public synchronized void shutdown() {
        if (!canShutdown()) {
            LOG.debug("ZooKeeper processor is not running, so not proceeding to shutdown!");
            return;
        }
        shutdown = true;
        unregisterJMX(this);

        // set peer's processor to null
        self.setZooKeeperServer(null);
        // clear all the connections
        self.closeAllConnections();
        // shutdown the processor itself
        super.shutdown();
    }

    @Override
    public void dumpConf(PrintWriter pwriter) {
        super.dumpConf(pwriter);

        pwriter.print("initLimit=");
        pwriter.println(self.getInitLimit());
        pwriter.print("syncLimit=");
        pwriter.println(self.getSyncLimit());
        pwriter.print("electionAlg=");
        pwriter.println(self.getElectionType());
        pwriter.print("electionPort=");
        pwriter.println(self.getElectionAddress().getPort());
        pwriter.print("quorumPort=");
        pwriter.println(self.getQuorumAddress().getPort());
        pwriter.print("peerType=");
        pwriter.println(self.getLearnerType().ordinal());
    }

    @Override
    protected void setState(ZKSState state) {
        this.state = state;
    }
}
