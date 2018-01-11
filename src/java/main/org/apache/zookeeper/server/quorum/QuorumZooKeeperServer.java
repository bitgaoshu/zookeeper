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
package org.apache.zookeeper.server.quorum;

import org.apache.zookeeper.exception.KeeperException;
import org.apache.zookeeper.nodeMode.CreateMode;
import org.apache.zookeeper.operation.Op;
import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.operation.multi.MultiTransactionRecord;
import org.apache.zookeeper.proto.CreateRequest;
import org.apache.zookeeper.server.common.ByteBufferInputStream;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.persistence.ZKDatabase;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

/**
 * Abstract base class for all ZooKeeperServers that participate in
 * a quorum.
 */
public abstract class QuorumZooKeeperServer extends ZooKeeperServer {

    public final QuorumPeer self;
    protected UpgradeableSessionTracker upgradeableSessionTracker;

    protected QuorumZooKeeperServer(FileTxnSnapLog logFactory,
                                    ZKDatabase zkDb, QuorumPeer self) {
        super(logFactory, self.getTickTime(), self.getMinSessionTimeout(), self.getMaxSessionTimeout(), zkDb);
        this.self = self;
    }

    @Override
    protected void startSessionTracker() {
        upgradeableSessionTracker = (UpgradeableSessionTracker) sessionTracker;
        upgradeableSessionTracker.start();
    }

    public Request checkUpgradeSession(Request request)
            throws IOException, KeeperException {
        // If this is a request for a local session and it is to
        // create an ephemeral node, then upgrade the session and return
        // a new session request for the leader.
        // This is called by the request processor thread (either follower
        // or observer request processor), which is unique to a learner.
        // So will not be called concurrently by two threads.
        OpType opCode = request.op;
        if ((opCode != OpType.create && opCode != OpType.create2 && opCode != OpType.multi) ||
                !upgradeableSessionTracker.isLocalSession(request.sessionId)) {
            return null;
        }

        if (OpType.multi == opCode) {
            MultiTransactionRecord multiTransactionRecord = new MultiTransactionRecord();
            request.request.rewind();
            ByteBufferInputStream.byteBuffer2Record(request.request, multiTransactionRecord);
            request.request.rewind();
            boolean containsEphemeralCreate = false;
            for (Op op : multiTransactionRecord) {
                if (op.getType() == OpType.create.getValue() || opCode.getValue() == OpType.create2.getValue()) {
                    CreateRequest createRequest = (CreateRequest) op.toRequestRecord();
                    CreateMode createMode = CreateMode.fromFlag(createRequest.getFlags());
                    if (createMode.isEphemeral()) {
                        containsEphemeralCreate = true;
                        break;
                    }
                }
            }
            if (!containsEphemeralCreate) {
                return null;
            }
        } else {
            CreateRequest createRequest = new CreateRequest();
            request.request.rewind();
            ByteBufferInputStream.byteBuffer2Record(request.request, createRequest);
            request.request.rewind();
            CreateMode createMode = CreateMode.fromFlag(createRequest.getFlags());
            if (!createMode.isEphemeral()) {
                return null;
            }
        }

        // Uh oh.  We need to upgrade before we can proceed.
        if (!self.isLocalSessionsUpgradingEnabled()) {
            throw new KeeperException.EphemeralOnLocalSessionException();
        }

        return makeUpgradeRequest(request.sessionId);
    }

    private Request makeUpgradeRequest(long sessionId) {
        // Make sure to atomically check local session status, upgrade
        // session, and make the session creation request.  This is to
        // avoid another thread upgrading the session in parallel.
        synchronized (upgradeableSessionTracker) {
            if (upgradeableSessionTracker.isLocalSession(sessionId)) {
                int timeout = upgradeableSessionTracker.upgradeSession(sessionId);
                ByteBuffer to = ByteBuffer.allocate(4);
                to.putInt(timeout);
                return new Request(
                        null, sessionId, 0, OpType.createSession, to, null);
            }
        }
        return null;
    }

    /**
     * Implements the SessionUpgrader interface,
     *
     * @param sessionId
     */
    public void upgrade(long sessionId) {
        Request request = makeUpgradeRequest(sessionId);
        if (request != null) {
            LOG.info("Upgrading session 0x" + Long.toHexString(sessionId));
            // This must be a global request
            submitRequest(request);
        }
    }

    @Override
    protected void setLocalSessionFlag(Request si) {
        // We need to set isLocalSession to tree for these type of request
        // so that the request processor can process them correctly.
        switch (si.op) {
            case createSession:
                if (self.areLocalSessionsEnabled()) {
                    // All new sessions local by default.
                    si.setLocalSession(true);
                }
                break;
            case closeSession:
                String reqType = "global";
                if (upgradeableSessionTracker.isLocalSession(si.sessionId)) {
                    si.setLocalSession(true);
                    reqType = "local";
                }
                LOG.info("Submitting " + reqType + " closeSession request"
                        + " for session 0x" + Long.toHexString(si.sessionId));
                break;
            default:
                break;
        }
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
        pwriter.println("membership: ");
        pwriter.print(new String(self.getQuorumVerifier().toString().getBytes()));
    }

    @Override
    protected void setState(State state) {
        this.state = state;
    }
}
