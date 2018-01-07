/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.server.quorum.roles.server;

import org.apache.zookeeper.server.SyncRequestProcessor;
import org.apache.zookeeper.server.ZKDatabase;
import org.apache.zookeeper.server.cnxn.ServerCnxn;
import org.apache.zookeeper.server.jmx.MBeanRegistry;
import org.apache.zookeeper.server.jmx.impl.DataTreeBean;
import org.apache.zookeeper.server.jmx.impl.ZooKeeperServerBean;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.CommitProcessor;
import org.apache.zookeeper.server.quorum.LearnerSessionTracker;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.mBean.impl.LocalPeerBean;
import org.apache.zookeeper.server.quorum.roles.Learner;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Parent class for all ZooKeeperServers for Learners
 */
public abstract class LearnerZooKeeperServer extends QuorumZooKeeperServer {

    /*
     * Request processors
     */
    protected CommitProcessor commitProcessor;
    protected SyncRequestProcessor syncProcessor;

    public LearnerZooKeeperServer(FileTxnSnapLog logFactory,
                                  ZKDatabase zkDb, QuorumPeer self) {
        super(logFactory, zkDb, self);
    }

    /**
     * Abstract method to return the learner associated with this server.
     * Since the Learner may change under our feet (when QuorumPeer reassigns
     * it) we can't simply take a reference here. Instead, we need the
     * subclasses to implement this.
     */
    abstract public Learner getLearner();

    /**
     * Returns the current state of the session tracker. This is only currently
     * used by a Learner to build a ping response packet.
     */
    protected Map<Long, Integer> getTouchSnapshot() {
        if (sessionTracker != null) {
            return ((LearnerSessionTracker) sessionTracker).snapshot();
        }
        return Collections.emptyMap();
    }

    /**
     * Returns the id of the associated QuorumPeer, which will do for a unique
     * id of this server.
     */
    @Override
    public long getServerId() {
        return self.getId();
    }

    @Override
    public void createSessionTracker() {
        sessionTracker = new LearnerSessionTracker(
                this, getZKDatabase().getSessionWithTimeOuts(),
                this.tickTime, self.getId(), self.areLocalSessionsEnabled(),
                getZooKeeperServerListener());
    }

    @Override
    protected void revalidateSession(ServerCnxn cnxn, long sessionId,
                                     int sessionTimeout) throws IOException {
        if (upgradeableSessionTracker.isLocalSession(sessionId)) {
            super.revalidateSession(cnxn, sessionId, sessionTimeout);
        } else {
            getLearner().validateSession(cnxn, sessionId, sessionTimeout);
        }
    }

    @Override
    public void registerJMX() {
        // register with JMX
        try {
            jmxDataTreeBean = new DataTreeBean(getZKDatabase().getDataTree());
            MBeanRegistry.getInstance().register(jmxDataTreeBean, jmxServerBean);
        } catch (Exception e) {
            LOG.warn("Failed to register with JMX", e);
            jmxDataTreeBean = null;
        }
    }

    public void registerJMX(ZooKeeperServerBean serverBean,
                            LocalPeerBean localPeerBean) {
        /* register with JMX */
        self.registerLEMBean();
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

    public void unregisterJMX(Learner peer) {
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
    public synchronized void shutdown() {
        if (!canShutdown()) {
            LOG.debug("ZooKeeper server is not running, so not proceeding to shutdown!");
            return;
        }
        LOG.info("Shutting down");
        try {
            super.shutdown();
        } catch (Exception e) {
            LOG.warn("Ignoring unexpected exception during shutdown", e);
        }
        try {
            if (syncProcessor != null) {
                syncProcessor.shutdown();
            }
        } catch (Exception e) {
            LOG.warn("Ignoring unexpected exception in syncprocessor shutdown",
                    e);
        }
    }
}
