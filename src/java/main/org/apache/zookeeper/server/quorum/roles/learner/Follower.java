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

package org.apache.zookeeper.server.quorum.roles.learner;

import org.apache.jute.Record;
import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.util.Time;
import org.apache.zookeeper.server.quorum.QuorumPacket;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.flexible.QuorumVerifier;
import org.apache.zookeeper.server.quorum.mBean.impl.FollowerBean;
import org.apache.zookeeper.server.quorum.roles.OpOfLeader;
import org.apache.zookeeper.server.quorum.roles.learner.server.FollowerZooKeeperServer;
import org.apache.zookeeper.server.util.IOUtils;
import org.apache.zookeeper.server.util.ZxidUtils;
import org.apache.zookeeper.txn.SetDataTxn;
import org.apache.zookeeper.txn.TxnHeader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * This class has the control logic for the Follower.
 */
public class Follower extends Learner {

    // This is the same object as this.zk, but we cache the downcast op
    final FollowerZooKeeperServer fzk;
    private long lastQueued;

    public Follower(QuorumPeer self, FollowerZooKeeperServer zk) {
        this.self = self;
        this.zk = zk;
        this.fzk = zk;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Follower ").append(sock);
        sb.append(" lastQueuedZxid:").append(lastQueued);
        sb.append(" pendingRevalidationCount:")
                .append(pendingRevalidations.size());
        return sb.toString();
    }

    /**
     * the main method called by the follower to follow the leader
     *
     */
    public void followLeader()  {
        self.end_fle = Time.currentElapsedTime();
        long electionTimeTaken = self.end_fle - self.start_fle;
        self.setElectionTimeTaken(electionTimeTaken);
        LOG.info("FOLLOWING - LEADER ELECTION TOOK - {} {}", electionTimeTaken,
                QuorumPeer.FLE_TIME_UNIT);
        self.start_fle = 0;
        self.end_fle = 0;
        fzk.registerJMX(new FollowerBean(this, zk), self.getJmxLocalPeerBean());
        try {
            InetSocketAddress addr = findLeader();
            try {
                connectToLeader(addr);
                long newEpochZxid = registerWithLeader(OpOfLeader.FOLLOWERINFO);
                if (self.isReconfigStateChange())
                    throw new Exception("learned about role change");
                //check to see if the leader zxid is lower than ours
                //this should never happen but is just a safety check
                long newEpoch = ZxidUtils.getEpochFromZxid(newEpochZxid);
                if (newEpoch < self.getAcceptedEpoch()) {
                    LOG.error("Proposed leader epoch " + ZxidUtils.zxidToString(newEpochZxid)
                            + " is less than our accepted epoch " + ZxidUtils.zxidToString(self.getAcceptedEpoch()));
                    throw new IOException("Error: Epoch of leader is lower");
                }
                syncWithLeader(newEpochZxid);
                QuorumPacket qp = new QuorumPacket();
                while (this.isRunning()) {
                    readPacket(qp);
                    processPacket(qp);
                }
            } catch (Exception e) {
                LOG.warn("Exception when following the leader", e);
                try {
                    sock.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                // clear pending revalidations
                pendingRevalidations.clear();
            }
        } finally {
            zk.unregisterJMX( this);
        }
    }

    /**
     * Examine the packet received in qp and dispatch based on its contents.
     *
     * @param qp
     * @throws IOException
     */
    protected void processPacket(QuorumPacket qp) throws Exception {
        OpOfLeader op = OpOfLeader.fromInt(qp.getType());
        switch (op) {
            case PING:
                ping(qp);
                break;
            case PROPOSAL:
                TxnHeader hdr = new TxnHeader();
                Record txn = IOUtils.deserializeTxn(qp.getData(), hdr);
                if (hdr.getZxid() != lastQueued + 1) {
                    LOG.warn("Got zxid 0x"
                            + Long.toHexString(hdr.getZxid())
                            + " expected 0x"
                            + Long.toHexString(lastQueued + 1));
                }
                lastQueued = hdr.getZxid();

                if (hdr.getType() == OpType.reconfig.getValue()) {
                    SetDataTxn setDataTxn = (SetDataTxn) txn;
                    QuorumVerifier qv = self.configFromString(new String(setDataTxn.getData()));
                    self.setLastSeenQuorumVerifier(qv, true);
                }

                fzk.logRequest(hdr, txn);
                break;
            case COMMIT:
                fzk.commit(qp.getZxid());
                break;

            case COMMITANDACTIVATE:
                // get the new configuration from the request
                Request request = fzk.pendingHead();
                SetDataTxn setDataTxn = (SetDataTxn) request.getTxn();
                QuorumVerifier qv = self.configFromString(new String(setDataTxn.getData()));

                // get new designated leader from (current) leader's message
                ByteBuffer buffer = ByteBuffer.wrap(qp.getData());
                long suggestedLeaderId = buffer.getLong();
                boolean majorChange =
                        self.processReconfig(qv, suggestedLeaderId, qp.getZxid(), true);
                // commit (writes the new config to ZK tree (/zookeeper/config)
                fzk.commit(qp.getZxid());
                if (majorChange) {
                    throw new Exception("changes proposed in reconfig");
                }
                break;
            case UPTODATE:
                LOG.error("Received an UPTODATE message after Follower started");
                break;
            case REVALIDATE:
                revalidate(qp);
                break;
            case SYNC:
                fzk.sync();
                break;
            default:
                LOG.warn("Unknown packet type: {}", IOUtils.serializePacket2String(qp));
                break;
        }
    }

    /**
     * The zxid of the last operation seen
     *
     * @return zxid
     */
    public long getZxid() {
        try {
            synchronized (fzk) {
                return fzk.getZxid();
            }
        } catch (NullPointerException e) {
            LOG.warn("error getting zxid", e);
        }
        return -1;
    }

    /**
     * The zxid of the last operation queued
     *
     * @return zxid
     */
    public long getLastQueued() {
        return lastQueued;
    }

    @Override
    public void shutdown() {
        LOG.info("shutdown called", new Exception("shutdown Follower"));
        super.shutdown();
    }

    @Override
    public ZooKeeperServer getZk() {
        return fzk;
    }
}
