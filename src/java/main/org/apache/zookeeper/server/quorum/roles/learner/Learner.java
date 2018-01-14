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

import org.apache.jute.BinaryInputArchive;
import org.apache.jute.BinaryOutputArchive;
import org.apache.jute.InputArchive;
import org.apache.jute.OutputArchive;
import org.apache.jute.Record;
import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.util.IOUtils;
import org.apache.zookeeper.server.util.ZooTrace;
import org.apache.zookeeper.server.cnxn.ServerCnxn;
import org.apache.zookeeper.server.quorum.LearnerInfo;
import org.apache.zookeeper.server.quorum.QuorumPacket;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeer.QuorumServer;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.quorum.Role;
import org.apache.zookeeper.server.quorum.election.Vote;
import org.apache.zookeeper.server.quorum.flexible.QuorumVerifier;
import org.apache.zookeeper.server.quorum.roles.OpOfLeader;
import org.apache.zookeeper.server.quorum.roles.learner.server.FollowerZooKeeperServer;
import org.apache.zookeeper.server.quorum.roles.learner.server.LearnerZooKeeperServer;
import org.apache.zookeeper.server.quorum.roles.learner.server.ObserverZooKeeperServer;
import org.apache.zookeeper.server.util.ZxidUtils;
import org.apache.zookeeper.txn.SetDataTxn;
import org.apache.zookeeper.txn.TxnHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is the superclass of two of the three main actors in a ZK
 * ensemble: Followers and Observers. Both Followers and Observers share 
 * a good deal of code which is moved into Peer to avoid duplication. 
 */
public class Learner  implements Role {
    protected static final Logger LOG = LoggerFactory.getLogger(Learner.class);
    static final private boolean nodelay = System.getProperty("follower.nodelay", "true").equals("true");

    static {
        LOG.info("TCP NoDelay set to: " + nodelay);
    }

    final ConcurrentHashMap<Long, ServerCnxn> pendingRevalidations =
            new ConcurrentHashMap<Long, ServerCnxn>();
    protected BufferedOutputStream bufferedOutput;
    protected Socket sock;
    protected InputArchive leaderIs;
    protected OutputArchive leaderOs;
    /** the protocol version of the leader */
    protected int leaderProtocolVersion = 0x01;
    QuorumPeer self;
    LearnerZooKeeperServer zk;

    /**
     * Socket getter
     * @return
     */
    public Socket getSocket() {
        return sock;
    }

    public int getPendingRevalidationsCount() {
        return pendingRevalidations.size();
    }

    /**
     * validate a session for a client
     *
     * @param clientId
     *                the client to be revalidated
     * @param timeout
     *                the timeout for which the session is valid
     * @return
     * @throws IOException
     */
    public void validateSession(ServerCnxn cnxn, long clientId, int timeout)
            throws IOException {
        LOG.info("Revalidating client: 0x" + Long.toHexString(clientId));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeLong(clientId);
        dos.writeInt(timeout);
        dos.close();
        QuorumPacket qp = new QuorumPacket(OpOfLeader.REVALIDATE.intType(), -1, baos
                .toByteArray(), null);
        pendingRevalidations.put(clientId, cnxn);
        if (LOG.isTraceEnabled()) {
            ZooTrace.logTraceMessage(LOG,
                    ZooTrace.SESSION_TRACE_MASK,
                    "To validate session 0x"
                            + Long.toHexString(clientId));
        }
        writePacket(qp, true);
    }

    /**
     * write a packet to the leader
     *
     * @param pp
     *                the proposal packet to be sent to the leader
     * @throws IOException
     */
    public void writePacket(QuorumPacket pp, boolean flush) throws IOException {
        synchronized (leaderOs) {
            if (pp != null) {
                leaderOs.writeRecord(pp, "packet");
            }
            if (flush) {
                bufferedOutput.flush();
            }
        }
    }

    /**
     * read a packet from the leader
     *
     * @param pp
     *                the packet to be instantiated
     * @throws IOException
     */
    void readPacket(QuorumPacket pp) throws IOException {
        synchronized (leaderIs) {
            leaderIs.readRecord(pp, "packet");
        }
        long traceMask = ZooTrace.SERVER_PACKET_TRACE_MASK;
        if (pp.getType() == OpOfLeader.PING.intType()) {
            traceMask = ZooTrace.SERVER_PING_TRACE_MASK;
        }
        if (LOG.isTraceEnabled()) {
            ZooTrace.logQuorumPacket(LOG, traceMask, 'i', pp);
        }
    }

    /**
     * send a request packet to the leader
     *
     * @param request
     *                the request from the client
     * @throws IOException
     */
    public void request(Request request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream oa = new DataOutputStream(baos);
        oa.writeLong(request.sessionId);
        oa.writeInt(request.cxid);
        oa.writeInt(request.op.getValue());
        if (request.request != null) {
            request.request.rewind();
            int len = request.request.remaining();
            byte b[] = new byte[len];
            request.request.get(b);
            request.request.rewind();
            oa.write(b);
        }
        oa.close();
        QuorumPacket qp = new QuorumPacket(OpOfLeader.REQUEST.intType(), -1, baos
                .toByteArray(), request.authInfo);
        writePacket(qp, true);
    }

    /**
     * Returns the address of the node we think is the leader.
     */
    protected InetSocketAddress findLeader() {
        InetSocketAddress addr = null;
        // Find the leader by id
        Vote current = self.getCurrentVote();
        for (QuorumServer s : self.getView().values()) {
            if (s.id == current.getId()) {
                addr = s.addr;
                break;
            }
        }
        if (addr == null) {
            LOG.warn("Couldn't find the leader with id = "
                    + current.getId());
        }
        return addr;
    }

    /**
     * Overridable helper method to return the System.nanoTime().
     * This method behaves identical to System.nanoTime().
     */
    protected long nanoTime() {
        return System.nanoTime();
    }

    /**
     * Overridable helper method to simply call sock.connect(). This can be
     * overriden in tests to fake connection success/failure for connectToLeader.
     */
    protected void sockConnect(Socket sock, InetSocketAddress addr, int timeout)
            throws IOException {
        sock.connect(addr, timeout);
    }

    /**
     * Establish a connection with the leader found by findLeader. Retries
     * until either initLimit time has elapsed or 5 tries have happened.
     * @param addr - the address of the leader to connect to.
     * @throws IOException - if the socket connection fails on the 5th attempt
     * @throws ConnectException
     * @throws InterruptedException
     */
    protected void connectToLeader(InetSocketAddress addr)
            throws IOException, ConnectException, InterruptedException {
        sock = new Socket();
        sock.setSoTimeout(self.getSoTimeout());

        int initLimitTime = self.getSoTimeout();
        int remainingInitLimitTime = initLimitTime;
        long startNanoTime = nanoTime();

        for (int tries = 0; tries < 5; tries++) {
            try {
                // recalculate the init limit time because retries sleep for 1000 milliseconds
                remainingInitLimitTime = initLimitTime - (int) ((nanoTime() - startNanoTime) / 1000000);
                if (remainingInitLimitTime <= 0) {
                    LOG.error("initLimit exceeded on retries.");
                    throw new IOException("initLimit exceeded on retries.");
                }

                sockConnect(sock, addr, Math.min(self.getSoTimeout(), remainingInitLimitTime));
                sock.setTcpNoDelay(nodelay);
                break;
            } catch (IOException e) {
                remainingInitLimitTime = initLimitTime - (int) ((nanoTime() - startNanoTime) / 1000000);

                if (remainingInitLimitTime <= 1000) {
                    LOG.error("Unexpected exception, initLimit exceeded. tries=" + tries +
                            ", remaining init limit=" + remainingInitLimitTime +
                            ", connecting to " + addr, e);
                    throw e;
                } else if (tries >= 4) {
                    LOG.error("Unexpected exception, retries exceeded. tries=" + tries +
                            ", remaining init limit=" + remainingInitLimitTime +
                            ", connecting to " + addr, e);
                    throw e;
                } else {
                    LOG.warn("Unexpected exception, tries=" + tries +
                            ", remaining init limit=" + remainingInitLimitTime +
                            ", connecting to " + addr, e);
                    sock = new Socket();
                    sock.setSoTimeout(self.getSoTimeout());
                }
            }
            Thread.sleep(1000);
        }
        leaderIs = BinaryInputArchive.getArchive(new BufferedInputStream(
                sock.getInputStream()));
        bufferedOutput = new BufferedOutputStream(sock.getOutputStream());
        leaderOs = BinaryOutputArchive.getArchive(bufferedOutput);
    }

    /**
     * Once connected to the leader, perform the handshake protocol to
     * establish a following / observing connection.
     * @param pktType
     * @return the zxid the leader sends for synchronization purposes.
     * @throws IOException
     */
    protected long registerWithLeader(OpOfLeader pktType) throws IOException {
        /*
         * Send follower info, including last zxid and sid
         */
        long lastLoggedZxid = self.getLastLoggedZxid();
        QuorumPacket qp = new QuorumPacket();
        qp.setType(pktType.intType());
        qp.setZxid(ZxidUtils.makeZxid(self.getAcceptedEpoch(), 0));

        /*
         * Add sid to payload
         */
        LearnerInfo li = new LearnerInfo(self.getId(), 0x10000, self.getQuorumVerifier().getVersion());
        ByteArrayOutputStream bsid = new ByteArrayOutputStream();
        BinaryOutputArchive boa = BinaryOutputArchive.getArchive(bsid);
        boa.writeRecord(li, "LearnerInfo");
        qp.setData(bsid.toByteArray());

        writePacket(qp, true);
        readPacket(qp);
        final long newEpoch = ZxidUtils.getEpochFromZxid(qp.getZxid());
        OpOfLeader op = OpOfLeader.fromInt(qp.getType());
        if (op == OpOfLeader.LEADERINFO) {
            // we are connected to a 1.0 processor so accept the new epoch and read the next packet
            leaderProtocolVersion = ByteBuffer.wrap(qp.getData()).getInt();
            byte epochBytes[] = new byte[4];
            final ByteBuffer wrappedEpochBytes = ByteBuffer.wrap(epochBytes);
            if (newEpoch > self.getAcceptedEpoch()) {
                wrappedEpochBytes.putInt((int) self.getCurrentEpoch());
                self.setAcceptedEpoch(newEpoch);
            } else if (newEpoch == self.getAcceptedEpoch()) {
                // since we have already acked an epoch equal to the leaders, we cannot ack
                // again, but we still need to send our lastZxid to the leader so that we can
                // sync with it if it does assume leadership of the epoch.
                // the -1 indicates that this reply should not count as an ack for the new epoch
                wrappedEpochBytes.putInt(-1);
            } else {
                throw new IOException("Leaders epoch, " + newEpoch + " is less than accepted epoch, " + self.getAcceptedEpoch());
            }
            QuorumPacket ackNewEpoch = new QuorumPacket(OpOfLeader.ACKEPOCH.intType(), lastLoggedZxid, epochBytes, null);
            writePacket(ackNewEpoch, true);
            return ZxidUtils.makeZxid(newEpoch, 0);
        } else {
            if (newEpoch > self.getAcceptedEpoch()) {
                self.setAcceptedEpoch(newEpoch);
            }
            if (op != OpOfLeader.NEWLEADER) {
                LOG.error("First packet should have been NEWLEADER");
                throw new IOException("First packet should have been NEWLEADER");
            }
            return qp.getZxid();
        }
    }

    /**
     * Finally, synchronize our history with the leader.
     * @param newLeaderZxid
     * @throws IOException
     * @throws InterruptedException
     */
    protected void syncWithLeader(long newLeaderZxid) throws Exception {
        QuorumPacket ack = new QuorumPacket(OpOfLeader.ACK.intType(), 0, null, null);
        QuorumPacket qp = new QuorumPacket();
        long newEpoch = ZxidUtils.getEpochFromZxid(newLeaderZxid);

        QuorumVerifier newLeaderQV = null;

        // In the DIFF case we don't need to do a snapshot because the transactions will sync on top of any existing snapshot
        // For SNAP and TRUNC the snapshot is needed to save that history
        boolean snapshotNeeded = true;
        boolean syncSnapshot = false;
        readPacket(qp);
        LinkedList<Long> packetsCommitted = new LinkedList<Long>();
        LinkedList<PacketInFlight> packetsNotCommitted = new LinkedList<PacketInFlight>();
        synchronized (zk) {
            OpOfLeader op = OpOfLeader.fromInt(qp.getType());
            if (op == OpOfLeader.DIFF) {
                LOG.info("Getting a diff from the leader 0x{}", Long.toHexString(qp.getZxid()));
                snapshotNeeded = false;
            } else if (op == OpOfLeader.SNAP) {
                LOG.info("Getting a snapshot from leader 0x" + Long.toHexString(qp.getZxid()));
                // The leader is going to dump the database
                // db is clear as part of deserializeSnapshot()
                zk.getZKDatabase().deserializeSnapshot(leaderIs);
                // ZOOKEEPER-2819: overwrite config node content extracted
                // from leader snapshot with local config, to avoid potential
                // inconsistency of config node content during rolling restart.
                if (!ServerConfig.isReconfigEnabled()) {
                    LOG.debug("Reset config node content from local config after deserialization of snapshot.");
                    zk.getZKDatabase().initConfigInZKDatabase(self.getQuorumVerifier());
                }
                String signature = leaderIs.readString("signature");
                if (!signature.equals("BenWasHere")) {
                    LOG.error("Missing signature. Got " + signature);
                    throw new IOException("Missing signature");
                }
                zk.getZKDatabase().setlastProcessedZxid(qp.getZxid());

                // immediately persist the latest snapshot when there is txn log gap
                syncSnapshot = true;
            } else if (op == OpOfLeader.TRUNC) {
                //we need to truncate the log to the lastzxid of the leader
                LOG.warn("Truncating log to get in sync with the leader 0x"
                        + Long.toHexString(qp.getZxid()));
                boolean truncated = zk.getZKDatabase().truncateLog(qp.getZxid());
                if (!truncated) {
                    // not able to truncate the log
                    LOG.error("Not able to truncate the log "
                            + Long.toHexString(qp.getZxid()));
                    System.exit(13);
                }
                zk.getZKDatabase().setlastProcessedZxid(qp.getZxid());

            } else {
                LOG.error("Got unexpected packet from leader: {}, exiting ... ",
                        IOUtils.serializePacket2String(qp));
                System.exit(13);

            }
            zk.getZKDatabase().initConfigInZKDatabase(self.getQuorumVerifier());
            zk.createSessionTracker();

            long lastQueued = 0;

            // in Zab V1.0 (ZK 3.4+) we might take a snapshot when we get the NEWLEADER message, but in pre V1.0
            // we take the snapshot on the UPDATE message, since Zab V1.0 also gets the UPDATE (after the NEWLEADER)
            // we need to make sure that we don't take the snapshot twice.
            boolean isPreZAB1_0 = true;
            //If we are not going to take the snapshot be sure the transactions are not applied in memory
            // but written out to the transaction log
            boolean writeToTxnLog = !snapshotNeeded;
            // we are now going to start getting transactions to apply followed by an UPTODATE
            outerLoop:
            while (self.isRunning()) {
                readPacket(qp);
                op = OpOfLeader.fromInt(qp.getType());
                switch (op) {
                    case PROPOSAL:
                        PacketInFlight pif = new PacketInFlight();
                        pif.hdr = new TxnHeader();
                        pif.rec = IOUtils.deserializeTxn(qp.getData(), pif.hdr);
                        if (pif.hdr.getZxid() != lastQueued + 1) {
                            LOG.warn("Got zxid 0x"
                                    + Long.toHexString(pif.hdr.getZxid())
                                    + " expected 0x"
                                    + Long.toHexString(lastQueued + 1));
                        }
                        lastQueued = pif.hdr.getZxid();

                        if (pif.hdr.getType() == OpType.reconfig.getValue()) {
                            SetDataTxn setDataTxn = (SetDataTxn) pif.rec;
                            QuorumVerifier qv = self.configFromString(new String(setDataTxn.getData()));
                            self.setLastSeenQuorumVerifier(qv, true);
                        }

                        packetsNotCommitted.add(pif);
                        break;
                    case COMMIT:
                    case COMMITANDACTIVATE:
                        pif = packetsNotCommitted.peekFirst();
                        if (pif.hdr.getZxid() == qp.getZxid() && op == OpOfLeader.COMMITANDACTIVATE) {
                            QuorumVerifier qv = self.configFromString(new String(((SetDataTxn) pif.rec).getData()));
                            boolean majorChange = self.processReconfig(qv, ByteBuffer.wrap(qp.getData()).getLong(),
                                    qp.getZxid(), true);
                            if (majorChange) {
                                throw new Exception("changes proposed in reconfig");
                            }
                        }
                        if (!writeToTxnLog) {
                            if (pif.hdr.getZxid() != qp.getZxid()) {
                                LOG.warn("Committing " + qp.getZxid() + ", but next proposal is " + pif.hdr.getZxid());
                            } else {
                                zk.processTxn(pif.hdr, pif.rec);
                                packetsNotCommitted.remove();
                            }
                        } else {
                            packetsCommitted.add(qp.getZxid());
                        }
                        break;
                    case INFORM:
                    case INFORMANDACTIVATE:
                        PacketInFlight packet = new PacketInFlight();
                        packet.hdr = new TxnHeader();

                        if (op == OpOfLeader.INFORMANDACTIVATE) {
                            ByteBuffer buffer = ByteBuffer.wrap(qp.getData());
                            long suggestedLeaderId = buffer.getLong();
                            byte[] remainingdata = new byte[buffer.remaining()];
                            buffer.get(remainingdata);
                            packet.rec = IOUtils.deserializeTxn(remainingdata, packet.hdr);
                            QuorumVerifier qv = self.configFromString(new String(((SetDataTxn) packet.rec).getData()));
                            boolean majorChange =
                                    self.processReconfig(qv, suggestedLeaderId, qp.getZxid(), true);
                            if (majorChange) {
                                throw new Exception("changes proposed in reconfig");
                            }
                        } else {
                            packet.rec = IOUtils.deserializeTxn(qp.getData(), packet.hdr);
                            // Log warning message if txn comes out-of-order
                            if (packet.hdr.getZxid() != lastQueued + 1) {
                                LOG.warn("Got zxid 0x"
                                        + Long.toHexString(packet.hdr.getZxid())
                                        + " expected 0x"
                                        + Long.toHexString(lastQueued + 1));
                            }
                            lastQueued = packet.hdr.getZxid();
                        }
                        if (!writeToTxnLog) {
                            // Apply to db directly if we haven't taken the snapshot
                            zk.processTxn(packet.hdr, packet.rec);
                        } else {
                            packetsNotCommitted.add(packet);
                            packetsCommitted.add(qp.getZxid());
                        }

                        break;
                    case UPTODATE:
                        LOG.info("Learner received UPTODATE message");
                        if (newLeaderQV != null) {
                            boolean majorChange =
                                    self.processReconfig(newLeaderQV, null, null, true);
                            if (majorChange) {
                                throw new Exception("changes proposed in reconfig");
                            }
                        }
                        if (isPreZAB1_0) {
                            zk.takeSnapshot(syncSnapshot);
                            self.setCurrentEpoch(newEpoch);
                        }
                        self.setZooKeeperServer(zk);
                        break outerLoop;
                    case NEWLEADER: // Getting NEWLEADER here instead of in discovery
                        // means this is Zab 1.0
                        LOG.info("Learner received NEWLEADER message");
                        if (qp.getData() != null && qp.getData().length > 1) {
                            try {
                                QuorumVerifier qv = self.configFromString(new String(qp.getData()));
                                self.setLastSeenQuorumVerifier(qv, true);
                                newLeaderQV = qv;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if (snapshotNeeded) {
                            zk.takeSnapshot(syncSnapshot);
                        }

                        self.setCurrentEpoch(newEpoch);
                        writeToTxnLog = true; //Anything after this needs to go to the transaction log, not applied directly in memory
                        isPreZAB1_0 = false;
                        writePacket(new QuorumPacket(OpOfLeader.ACK.intType(), newLeaderZxid, null, null), true);
                        break;
                }
            }
        }
        ack.setZxid(ZxidUtils.makeZxid(newEpoch, 0));
        writePacket(ack, true);
        sock.setSoTimeout(self.getSoTimeout());
        zk.startup();
        /*
         * Update the election vote here to ensure that all members of the
         * ensemble report the same vote to new servers that start up and
         * send leader election notifications to the ensemble.
         *
         * @see https://issues.apache.org/jira/browse/ZOOKEEPER-1732
         */
        self.updateElectionVote(newEpoch);

        // We need to log the stuff that came in between the snapshot and the uptodate
        if (zk instanceof FollowerZooKeeperServer) {
            FollowerZooKeeperServer fzk = (FollowerZooKeeperServer) zk;
            for (PacketInFlight p : packetsNotCommitted) {
                fzk.logRequest(p.hdr, p.rec);
            }
            for (Long zxid : packetsCommitted) {
                fzk.commit(zxid);
            }
        } else if (zk instanceof ObserverZooKeeperServer) {
            // Similar to follower, we need to log requests between the snapshot
            // and UPTODATE
            ObserverZooKeeperServer ozk = (ObserverZooKeeperServer) zk;
            for (PacketInFlight p : packetsNotCommitted) {
                Long zxid = packetsCommitted.peekFirst();
                if (p.hdr.getZxid() != zxid) {
                    // log warning message if there is no matching commit
                    // old leader send outstanding proposal to observer
                    LOG.warn("Committing " + Long.toHexString(zxid)
                            + ", but next proposal is "
                            + Long.toHexString(p.hdr.getZxid()));
                    continue;
                }
                packetsCommitted.remove();
                int opValue = p.hdr.getType();
                Request request = new Request(null, p.hdr.getClientId(),
                        p.hdr.getCxid(), OpType.getOpCode(opValue), null, null);
                request.setTxn(p.rec);
                request.setHdr(p.hdr);
                ozk.commitRequest(request);
            }
        } else {
            // New processor type need to handle in-flight packets
            throw new UnsupportedOperationException("Unknown processor type");
        }
    }

    protected void revalidate(QuorumPacket qp) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(qp
                .getData());
        DataInputStream dis = new DataInputStream(bis);
        long sessionId = dis.readLong();
        boolean valid = dis.readBoolean();
        ServerCnxn cnxn = pendingRevalidations.remove(sessionId);
        if (cnxn == null) {
            LOG.warn("Missing session 0x"
                    + Long.toHexString(sessionId)
                    + " for validation");
        } else {
            zk.finishSessionInit(cnxn, valid);
        }
        if (LOG.isTraceEnabled()) {
            ZooTrace.logTraceMessage(LOG,
                    ZooTrace.SESSION_TRACE_MASK,
                    "Session 0x" + Long.toHexString(sessionId)
                            + " is valid: " + valid);
        }
    }

    protected void ping(QuorumPacket qp) throws IOException {
        // Send back the ping with our session data
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        Map<Long, Integer> touchTable = zk.getTouchSnapshot();
        for (Entry<Long, Integer> entry : touchTable.entrySet()) {
            dos.writeLong(entry.getKey());
            dos.writeInt(entry.getValue());
        }
        qp.setData(bos.toByteArray());
        writePacket(qp, true);
    }

    /**
     * Shutdown the Peer
     */
    public void shutdown() {
        self.setZooKeeperServer(null);
        self.closeAllConnections();
        // shutdown previous zookeeper
        if (zk != null) {
            zk.shutdown();
        }
    }

    boolean isRunning() {
        return self.isRunning() && zk.isRunning();
    }

    @Override
    public QuorumPeer getQuorumPeer() {
        return self;
    }

    static class PacketInFlight {
        TxnHeader hdr;
        Record rec;
    }
}
