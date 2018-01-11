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

package org.apache.zookeeper.server.processor;

import org.apache.jute.BinaryOutputArchive;
import org.apache.jute.Record;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.StatPersisted;
import org.apache.zookeeper.exception.KeeperException;
import org.apache.zookeeper.exception.KeeperException.BadArgumentsException;
import org.apache.zookeeper.exception.KeeperException.KECode;
import org.apache.zookeeper.nodeMode.CreateMode;
import org.apache.zookeeper.nodeMode.EphemeralType;
import org.apache.zookeeper.operation.Op;
import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.operation.multi.MultiTransactionRecord;
import org.apache.zookeeper.proto.CheckVersionRequest;
import org.apache.zookeeper.proto.CreateRequest;
import org.apache.zookeeper.proto.CreateTTLRequest;
import org.apache.zookeeper.proto.DeleteRequest;
import org.apache.zookeeper.proto.ReconfigRequest;
import org.apache.zookeeper.proto.SetACLRequest;
import org.apache.zookeeper.proto.SetDataRequest;
import org.apache.zookeeper.server.common.ByteBufferInputStream;
import org.apache.zookeeper.server.exception.RequestProcessorException;
import org.apache.zookeeper.server.persistence.DataNode;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.zkThread.ZooKeeperCriticalThread;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.util.ZooTrace;
import org.apache.zookeeper.server.auth.ProviderRegistry;
import org.apache.zookeeper.server.auth.ServerAuthenticationProvider;
import org.apache.zookeeper.server.cnxn.ServerCnxn;
import org.apache.zookeeper.server.util.PathUtils;
import org.apache.zookeeper.server.util.StringUtils;
import org.apache.zookeeper.server.common.Time;
import org.apache.zookeeper.server.quorum.QuorumPeer.QuorumServer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.exception.ConfigException;
import org.apache.zookeeper.server.quorum.flexible.QuorumMaj;
import org.apache.zookeeper.server.quorum.flexible.QuorumVerifier;
import org.apache.zookeeper.server.quorum.roles.leader.Leader.XidRolloverException;
import org.apache.zookeeper.server.quorum.roles.leader.LeaderZooKeeperServer;
import org.apache.zookeeper.txn.CheckVersionTxn;
import org.apache.zookeeper.txn.CreateContainerTxn;
import org.apache.zookeeper.txn.CreateSessionTxn;
import org.apache.zookeeper.txn.CreateTTLTxn;
import org.apache.zookeeper.txn.CreateTxn;
import org.apache.zookeeper.txn.DeleteTxn;
import org.apache.zookeeper.txn.ErrorTxn;
import org.apache.zookeeper.txn.MultiTxn;
import org.apache.zookeeper.txn.SetACLTxn;
import org.apache.zookeeper.txn.SetDataTxn;
import org.apache.zookeeper.txn.Txn;
import org.apache.zookeeper.txn.TxnHeader;
import org.apache.zookeeper.util.ZooDefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This request processor is generally at the start of a RequestProcessor
 * change. It sets up any transactions associated with requests that change the
 * state of the system. It counts on ZooKeeperServer to update
 * outstandingRequests, so that it can take into account transactions that are
 * in the queue to be applied when generating a transaction.
 */
public class PrepRequestProcessor extends ZooKeeperCriticalThread implements
        RequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(PrepRequestProcessor.class);

    static boolean skipACL;
    /**
     * this is only for testing purposes.
     * should never be used otherwise
     */
    private static boolean failCreate = false;

    static {
        skipACL = System.getProperty("zookeeper.skipACL", "no").equals("yes");
        if (skipACL) {
            LOG.info("zookeeper.skipACL==\"yes\", ACL checks will be skipped");
        }
    }

    private final RequestProcessor nextProcessor;
    LinkedBlockingQueue<Request> submittedRequests = new LinkedBlockingQueue<Request>();
    ZooKeeperServer zks;

    public PrepRequestProcessor(ZooKeeperServer zks,
                                RequestProcessor nextProcessor) {
        super("ProcessThread(sid:" + zks.getServerId() + " cport:"
                + zks.getClientPort() + "):", zks.getZooKeeperServerListener());
        this.nextProcessor = nextProcessor;
        this.zks = zks;
    }

    /**
     * method for tests to set failCreate
     *
     * @param b
     */
    public static void setFailCreate(boolean b) {
        failCreate = b;
    }

    /**
     * Grant or deny authorization to an operation on a node as a function of:
     *
     * @param zks     :     the ZooKeeper processor
     * @param cnxn    :    the processor connection
     * @param acl     :     set of ACLs for the node
     * @param perm    :    the permission that the client is requesting
     * @param ids     :     the credentials supplied by the client
     * @param path    :    the ZNode path
     * @param setAcls : for set ACL operations, the list of ACLs being set. Otherwise null.
     */
    static void checkACL(ZooKeeperServer zks, ServerCnxn cnxn, List<ACL> acl, int perm, List<Id> ids,
                         String path, List<ACL> setAcls) throws KeeperException.NoAuthException {
        if (skipACL) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Permission requested: {} ", perm);
            LOG.debug("ACLs for node: {}", acl);
            LOG.debug("Client credentials: {}", ids);
        }
        if (acl == null || acl.size() == 0) {
            return;
        }
        for (Id authId : ids) {
            if (authId.getScheme().equals("super")) {
                return;
            }
        }
        for (ACL a : acl) {
            Id id = a.getId();
            if ((a.getPerms() & perm) != 0) {
                if (id.getScheme().equals("world")
                        && id.getId().equals("anyone")) {
                    return;
                }
                ServerAuthenticationProvider ap = ProviderRegistry.getServerProvider(id
                        .getScheme());
                if (ap != null) {
                    for (Id authId : ids) {
                        if (authId.getScheme().equals(id.getScheme())
                                && ap.matches(new ServerAuthenticationProvider.ServerObjs(zks, cnxn),
                                new ServerAuthenticationProvider.MatchValues(path, authId.getId(), id.getId(), perm, setAcls))) {
                            return;
                        }
                    }
                }
            }
        }
        throw new KeeperException.NoAuthException();
    }

    private static int checkAndIncVersion(int currentVersion, int expectedVersion, String path)
            throws KeeperException.BadVersionException {
        if (expectedVersion != -1 && expectedVersion != currentVersion) {
            throw new KeeperException.BadVersionException(path);
        }
        return currentVersion + 1;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Request request = submittedRequests.take();
                long traceMask = ZooTrace.CLIENT_REQUEST_TRACE_MASK;
                if (request.op == OpType.ping) {
                    traceMask = ZooTrace.CLIENT_PING_TRACE_MASK;
                }
                if (LOG.isTraceEnabled()) {
                    ZooTrace.logRequest(LOG, traceMask, 'P', request, "");
                }
                if (Request.requestOfDeath == request) {
                    break;
                }
                pRequest(request);
            }
        } catch (RequestProcessorException e) {
            if (e.getCause() instanceof XidRolloverException) {
                LOG.info(e.getCause().getMessage());
            }
            handleException(this.getName(), e);
        } catch (Exception e) {
            handleException(this.getName(), e);
        }
        LOG.info("PrepRequestProcessor exited loop!");
    }

    private ChangeRecord getRecordForPath(String path) throws KeeperException.NoNodeException {
        ChangeRecord lastChange = synchronizedOutstandingChange(() -> {
            ChangeRecord cr = zks.getOutstandingChangesForPath().get(path);
            if (cr == null) {
                DataNode n = zks.getZKDatabase().getNode(path);
                if (n != null) {
                    Set<String> children;
                    synchronized (n) {
                        children = n.getChildren();
                    }
                    cr = new ChangeRecord(-1, path, n.stat, children.size(),
                            zks.getZKDatabase().aclForNode(n));
                }
            }
            return cr;
        });
        if (lastChange == null || lastChange.stat == null) {
            throw new KeeperException.NoNodeException(path);
        }
        return lastChange;
    }

    private ChangeRecord getOutstandingChange(String path) {
        return synchronizedOutstandingChange(
                () -> zks.getOutstandingChangesForPath().get(path)
        );
    }

    private void addChangeRecord(ChangeRecord c) {
        synchronizedOutstandingChange(() -> {
            zks.getOutstandingChanges().add(c);
            zks.getOutstandingChangesForPath().put(c.path, c);
            return null;
        });
    }

    private ChangeRecord synchronizedOutstandingChange(Fun fun) {
        synchronized (zks.getOutstandingChanges()) {
            return fun.apply();
        }
    }

    /**
     * Grab current pending change records for each op in a multi-op.
     * <p>
     * This is used inside MultiOp error code path to rollback in the event
     * of a failed multi-op.
     *
     * @param multiRequest
     * @return a map that contains previously existed records that probably need to be
     * rolled back in any failure.
     */
    private Map<String, ChangeRecord> getPendingChanges(MultiTransactionRecord multiRequest) {
        HashMap<String, ChangeRecord> pendingChangeRecords = new HashMap<String, ChangeRecord>();

        for (Op op : multiRequest) {
            String path = op.getPath();
            ChangeRecord cr = getOutstandingChange(path);
            // only previously existing records need to be rolled back.
            if (cr != null) {
                pendingChangeRecords.put(path, cr);
            }

            /*
             * ZOOKEEPER-1624 - We need to store for parent's ChangeRecord
             * of the parent node of a request. So that if this is a
             * sequential node creation request, rollbackPendingChanges()
             * can restore previous parent's ChangeRecord correctly.
             *
             * Otherwise, sequential node name generation will be incorrect
             * for a subsequent request.
             */
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash == -1 || path.indexOf('\0') != -1) {
                continue;
            }
            String parentPath = path.substring(0, lastSlash);
            ChangeRecord parentCr = getOutstandingChange(parentPath);
            if (parentCr != null) {
                pendingChangeRecords.put(parentPath, parentCr);
            }
        }

        return pendingChangeRecords;
    }

    /**
     * Rollback pending changes records from a failed multi-op.
     * <p>
     * If a multi-op fails, we can't leave any invalid change records we created
     * around. We also need to restore their prior value (if any) if their prior
     * value is still valid.
     *
     * @param zxid
     * @param pendingChangeRecords
     */
    void rollbackPendingChanges(long zxid, Map<String, ChangeRecord> pendingChangeRecords) {
        List<ChangeRecord> outstandingChanges = zks.getOutstandingChanges();
        Map<String, ChangeRecord> outstandingChangesForPath = zks.getOutstandingChangesForPath();
        synchronized (outstandingChanges) {
            // Grab a map iterator starting at the END of the map so we can iterate in reverse
            ListIterator<ChangeRecord> iter = outstandingChanges.listIterator(outstandingChanges.size());
            while (iter.hasPrevious()) {
                ChangeRecord c = iter.previous();
                if (c.zxid == zxid) {
                    iter.remove();
                    // Remove all outstanding changes for paths of this multi.
                    // Previous records will be added back later.
                    outstandingChangesForPath.remove(c.path);
                } else {
                    break;
                }
            }

            // we don't need to roll back any records because there is nothing left.
            if (outstandingChanges.isEmpty()) {
                return;
            }

            long firstZxid = outstandingChanges.get(0).zxid;

            for (ChangeRecord c : pendingChangeRecords.values()) {
                // Don't apply any prior change records less than firstZxid.
                // Note that previous outstanding requests might have been removed
                // once they are completed.
                if (c.zxid < firstZxid) {
                    continue;
                }

                // add previously existing records back.
                outstandingChangesForPath.put(c.path, c);
            }
        }
    }

    /**
     * Performs basic validation of a path for a create request.
     * Throws if the path is not valid and returns the parent path.
     *
     * @throws BadArgumentsException
     */
    private String validatePathForCreate(String path, long sessionId)
            throws BadArgumentsException {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1 || path.indexOf('\0') != -1 || failCreate) {
            LOG.info("Invalid path %s with session 0x%s",
                    path, Long.toHexString(sessionId));
            throw new KeeperException.BadArgumentsException(path);
        }
        return path.substring(0, lastSlash);
    }

    /**
     * This method will be called inside the ProcessRequestThread, which is a
     * singleton, so there will be a single thread calling this code.
     *
     * @param op      OpType
     * @param zxid
     * @param request
     * @param record
     */
    protected void pRequest2Txn(OpType op, long zxid, Request request,
                                Record record, boolean deserialize)
            throws KeeperException, IOException, RequestProcessorException {
        request.setHdr(new TxnHeader(request.sessionId, request.cxid, zxid,
                Time.currentWallTime(), op.getValue()));

        switch (op) {
            case create:
            case create2:
            case createTTL:
            case createContainer: {
                pRequest2TxnCreate(op, request, record, deserialize);
                break;
            }
            case deleteContainer: {
                String path = new String(request.request.array());
                String parentPath = getParentPathAndValidate(path);
                ChangeRecord parentRecord = getRecordForPath(parentPath);
                ChangeRecord nodeRecord = getRecordForPath(path);
                if (nodeRecord.childCount > 0) {
                    throw new KeeperException.NotEmptyException(path);
                }
                if (EphemeralType.get(nodeRecord.stat.getEphemeralOwner()) == EphemeralType.NORMAL) {
                    throw new KeeperException.BadVersionException(path);
                }
                request.setTxn(new DeleteTxn(path));
                parentRecord = parentRecord.duplicate(request.getHdr().getZxid());
                parentRecord.childCount--;
                addChangeRecord(parentRecord);
                addChangeRecord(new ChangeRecord(request.getHdr().getZxid(), path, null, -1, null));
                break;
            }
            case delete:
                zks.getSessionTracker().checkSession(request.sessionId, request.getOwner());
                DeleteRequest deleteRequest = (DeleteRequest) record;
                if (deserialize)
                    ByteBufferInputStream.byteBuffer2Record(request.request, deleteRequest);
                String path = deleteRequest.getPath();
                String parentPath = getParentPathAndValidate(path);
                ChangeRecord parentRecord = getRecordForPath(parentPath);
                ChangeRecord nodeRecord = getRecordForPath(path);
                checkACL(zks, request.cnxn, parentRecord.acl, ZooDefs.Perms.DELETE, request.authInfo, path, null);
                checkAndIncVersion(nodeRecord.stat.getVersion(), deleteRequest.getVersion(), path);
                if (nodeRecord.childCount > 0) {
                    throw new KeeperException.NotEmptyException(path);
                }
                request.setTxn(new DeleteTxn(path));
                parentRecord = parentRecord.duplicate(request.getHdr().getZxid());
                parentRecord.childCount--;
                addChangeRecord(parentRecord);
                addChangeRecord(new ChangeRecord(request.getHdr().getZxid(), path, null, -1, null));
                break;
            case setData:
                zks.getSessionTracker().checkSession(request.sessionId, request.getOwner());
                SetDataRequest setDataRequest = (SetDataRequest) record;
                if (deserialize)
                    ByteBufferInputStream.byteBuffer2Record(request.request, setDataRequest);
                path = setDataRequest.getPath();
                validatePath(path, request.sessionId);
                nodeRecord = getRecordForPath(path);
                checkACL(zks, request.cnxn, nodeRecord.acl, ZooDefs.Perms.WRITE, request.authInfo, path, null);
                int newVersion = checkAndIncVersion(nodeRecord.stat.getVersion(), setDataRequest.getVersion(), path);
                request.setTxn(new SetDataTxn(path, setDataRequest.getData(), newVersion));
                nodeRecord = nodeRecord.duplicate(request.getHdr().getZxid());
                nodeRecord.stat.setVersion(newVersion);
                addChangeRecord(nodeRecord);
                break;
            case reconfig:
                if (!QuorumPeerConfig.isReconfigEnabled()) {
                    LOG.error("Reconfig operation requested but reconfig feature is disabled.");
                    throw new KeeperException.ReconfigDisabledException();
                }

                if (skipACL) {
                    LOG.warn("skipACL is set, reconfig operation will skip ACL checks!");
                }

                zks.getSessionTracker().checkSession(request.sessionId, request.getOwner());
                ReconfigRequest reconfigRequest = (ReconfigRequest) record;
                LeaderZooKeeperServer lzks;
                try {
                    lzks = (LeaderZooKeeperServer) zks;
                } catch (ClassCastException e) {
                    // standalone mode - reconfiguration currently not supported
                    throw new KeeperException.UnimplementedException();
                }
                QuorumVerifier lastSeenQV = lzks.self.getLastSeenQuorumVerifier();
                // check that there's no reconfig in progress
                if (lastSeenQV.getVersion() != lzks.self.getQuorumVerifier().getVersion()) {
                    throw new KeeperException.ReconfigInProgressException();
                }
                long configId = reconfigRequest.getCurConfigId();

                if (configId != -1 && configId != lzks.self.getLastSeenQuorumVerifier().getVersion()) {
                    String msg = "Reconfiguration from version " + configId + " failed -- last seen version is " +
                            lzks.self.getLastSeenQuorumVerifier().getVersion();
                    throw new KeeperException.BadVersionException(msg);
                }

                String newMembers = reconfigRequest.getNewMembers();

                if (newMembers != null) { //non-incremental membership change
                    LOG.info("Non-incremental reconfig");

                    // Input may be delimited by either commas or newlines so convert to util newline separated format
                    newMembers = newMembers.replaceAll(",", "\n");

                    try {
                        Properties props = new Properties();
                        props.load(new StringReader(newMembers));
                        request.qv = QuorumPeerConfig.parseDynamicConfig(props, true, false);
                        request.qv.setVersion(request.getHdr().getZxid());
                    } catch (IOException | ConfigException e) {
                        throw new KeeperException.BadArgumentsException(e.getMessage());
                    }
                } else { //incremental change - must be a majority quorum system
                    LOG.info("Incremental reconfig");

                    List<String> joiningServers = null;
                    String joiningServersString = reconfigRequest.getJoiningServers();
                    if (joiningServersString != null) {
                        joiningServers = StringUtils.split(joiningServersString, ",");
                    }

                    List<String> leavingServers = null;
                    String leavingServersString = reconfigRequest.getLeavingServers();
                    if (leavingServersString != null) {
                        leavingServers = StringUtils.split(leavingServersString, ",");
                    }

                    if (!(lastSeenQV instanceof QuorumMaj)) {
                        String msg = "Incremental reconfiguration requested but last configuration seen has a non-majority quorum system";
                        LOG.warn(msg);
                        throw new KeeperException.BadArgumentsException(msg);
                    }
                    Map<Long, QuorumServer> nextServers = new HashMap<Long, QuorumServer>(lastSeenQV.getAllMembers());
                    try {
                        if (leavingServers != null) {
                            for (String leaving : leavingServers) {
                                long sid = Long.parseLong(leaving);
                                nextServers.remove(sid);
                            }
                        }
                        if (joiningServers != null) {
                            for (String joiner : joiningServers) {
                                // joiner should have the following format: processor.x = server_spec;client_spec
                                String[] parts = StringUtils.split(joiner, "=").toArray(new String[0]);
                                if (parts.length != 2) {
                                    throw new KeeperException.BadArgumentsException("Wrong format of processor string");
                                }
                                // extract processor id x from first part of joiner: processor.x
                                Long sid = Long.parseLong(parts[0].substring(parts[0].lastIndexOf('.') + 1));
                                QuorumServer qs = new QuorumServer(sid, parts[1]);
                                if (qs.clientAddr == null || qs.electionAddr == null || qs.addr == null) {
                                    throw new KeeperException.BadArgumentsException("Wrong format of processor string - each processor should have 3 ports specified");
                                }

                                // check duplication of addresses and ports
                                for (QuorumServer nqs : nextServers.values()) {
                                    if (qs.id == nqs.id) {
                                        continue;
                                    }
                                    qs.checkAddressDuplicate(nqs);
                                }

                                nextServers.remove(qs.id);
                                nextServers.put(qs.id, qs);
                            }
                        }
                    } catch (ConfigException e) {
                        throw new KeeperException.BadArgumentsException("Reconfiguration failed");
                    }
                    request.qv = new QuorumMaj(nextServers);
                    request.qv.setVersion(request.getHdr().getZxid());
                }
                if (QuorumPeerConfig.isStandaloneEnabled() && request.qv.getVotingMembers().size() < 2) {
                    String msg = "Reconfig failed - new configuration must include at least 2 followers";
                    LOG.warn(msg);
                    throw new KeeperException.BadArgumentsException(msg);
                } else if (request.qv.getVotingMembers().size() < 1) {
                    String msg = "Reconfig failed - new configuration must include at least 1 follower";
                    LOG.warn(msg);
                    throw new KeeperException.BadArgumentsException(msg);
                }

                if (!lzks.getLeader().isQuorumSynced(request.qv)) {
                    String msg2 = "Reconfig failed - there must be a connected and synced quorum in new configuration";
                    LOG.warn(msg2);
                    throw new KeeperException.NewConfigNoQuorumException();
                }

                nodeRecord = getRecordForPath(ZooDefs.CONFIG_NODE);
                checkACL(zks, request.cnxn, nodeRecord.acl, ZooDefs.Perms.WRITE, request.authInfo, null, null);
                request.setTxn(new SetDataTxn(ZooDefs.CONFIG_NODE, request.qv.toString().getBytes(), -1));
                nodeRecord = nodeRecord.duplicate(request.getHdr().getZxid());
                nodeRecord.stat.setVersion(-1);
                addChangeRecord(nodeRecord);
                break;
            case setACL:
                zks.getSessionTracker().checkSession(request.sessionId, request.getOwner());
                SetACLRequest setAclRequest = (SetACLRequest) record;
                if (deserialize)
                    ByteBufferInputStream.byteBuffer2Record(request.request, setAclRequest);
                path = setAclRequest.getPath();
                validatePath(path, request.sessionId);
                List<ACL> listACL = fixupACL(path, request.authInfo, setAclRequest.getAcl());
                nodeRecord = getRecordForPath(path);
                checkACL(zks, request.cnxn, nodeRecord.acl, ZooDefs.Perms.ADMIN, request.authInfo, path, listACL);
                newVersion = checkAndIncVersion(nodeRecord.stat.getAversion(), setAclRequest.getVersion(), path);
                request.setTxn(new SetACLTxn(path, listACL, newVersion));
                nodeRecord = nodeRecord.duplicate(request.getHdr().getZxid());
                nodeRecord.stat.setAversion(newVersion);
                addChangeRecord(nodeRecord);
                break;
            case createSession:
                request.request.rewind();
                int to = request.request.getInt();
                request.setTxn(new CreateSessionTxn(to));
                request.request.rewind();
                if (request.isLocalSession()) {
                    // This will add to local session tracker if it is enabled
                    zks.getSessionTracker().addSession(request.sessionId, to);
                } else {
                    // Explicitly add to global session if the flag is not set
                    zks.getSessionTracker().addGlobalSession(request.sessionId, to);
                }
                zks.setOwner(request.sessionId, request.getOwner());
                break;
            case closeSession:
                // We don't want to do this check since the session expiration thread
                // queues up this operation without being the session owner.
                // this request is the last of the session so it should be ok
                //zks.getSessionTracker().checkSession(request.sessionId, request.getOwner());
                Set<String> es = zks.getZKDatabase()
                        .getEphemerals(request.sessionId);
                synchronizedOutstandingChange(() -> {
                    for (ChangeRecord c : zks.getOutstandingChanges()) {
                        if (c.stat == null) {
                            // Doing a delete
                            es.remove(c.path);
                        } else if (c.stat.getEphemeralOwner() == request.sessionId) {
                            es.add(c.path);
                        }
                    }
                    for (String path2Delete : es) {
                        addChangeRecord(new ChangeRecord(request.getHdr().getZxid(), path2Delete, null, 0, null));
                    }

                    zks.getSessionTracker().setSessionClosing(request.sessionId);
                    return null;
                });

                LOG.info("Processed session termination for sessionid: 0x"
                        + Long.toHexString(request.sessionId));
                break;
            case check:
                zks.getSessionTracker().checkSession(request.sessionId, request.getOwner());
                CheckVersionRequest checkVersionRequest = (CheckVersionRequest) record;
                if (deserialize)
                    ByteBufferInputStream.byteBuffer2Record(request.request, checkVersionRequest);
                path = checkVersionRequest.getPath();
                validatePath(path, request.sessionId);
                nodeRecord = getRecordForPath(path);
                checkACL(zks, request.cnxn, nodeRecord.acl, ZooDefs.Perms.READ, request.authInfo, path, null);
                request.setTxn(new CheckVersionTxn(path, checkAndIncVersion(nodeRecord.stat.getVersion(),
                        checkVersionRequest.getVersion(), path)));
                break;
            default:
                LOG.warn("unknown OpType " + op);
                break;
        }
    }

    private void pRequest2TxnCreate(OpType op, Request request, Record record, boolean deserialize) throws IOException, KeeperException {
        if (deserialize) {
            ByteBufferInputStream.byteBuffer2Record(request.request, record);
        }

        int flags;
        String path;
        List<ACL> acl;
        byte[] data;
        long ttl;
        if (op == OpType.createTTL) {
            CreateTTLRequest createTtlRequest = (CreateTTLRequest) record;
            flags = createTtlRequest.getFlags();
            path = createTtlRequest.getPath();
            acl = createTtlRequest.getAcl();
            data = createTtlRequest.getData();
            ttl = createTtlRequest.getTtl();
        } else {
            CreateRequest createRequest = (CreateRequest) record;
            flags = createRequest.getFlags();
            path = createRequest.getPath();
            acl = createRequest.getAcl();
            data = createRequest.getData();
            ttl = -1;
        }
        CreateMode createMode = CreateMode.fromFlag(flags);
        validateCreateRequest(path, createMode, request, ttl);
        String parentPath = validatePathForCreate(path, request.sessionId);

        List<ACL> listACL = fixupACL(path, request.authInfo, acl);
        ChangeRecord parentRecord = getRecordForPath(parentPath);

        checkACL(zks, request.cnxn, parentRecord.acl, ZooDefs.Perms.CREATE, request.authInfo, path, listACL);
        int parentCVersion = parentRecord.stat.getCversion();
        if (createMode.isSequential()) {
            path = path + String.format(Locale.ENGLISH, "%010d", parentCVersion);
        }
        validatePath(path, request.sessionId);
        try {
            if (getRecordForPath(path) != null) {
                throw new KeeperException.NodeExistsException(path);
            }
        } catch (KeeperException.NoNodeException e) {
            // ignore this one
        }
        boolean ephemeralParent = EphemeralType.get(parentRecord.stat.getEphemeralOwner()) == EphemeralType.NORMAL;
        if (ephemeralParent) {
            throw new KeeperException.NoChildrenForEphemeralsException(path);
        }
        int newCversion = parentRecord.stat.getCversion() + 1;
        if (op == OpType.createContainer) {
            request.setTxn(new CreateContainerTxn(path, data, listACL, newCversion));
        } else if (op == OpType.createTTL) {
            request.setTxn(new CreateTTLTxn(path, data, listACL, newCversion, ttl));
        } else {
            request.setTxn(new CreateTxn(path, data, listACL, createMode.isEphemeral(),
                    newCversion));
        }
        StatPersisted s = new StatPersisted();
        if (createMode.isEphemeral()) {
            s.setEphemeralOwner(request.sessionId);
        }
        parentRecord = parentRecord.duplicate(request.getHdr().getZxid());
        parentRecord.childCount++;
        parentRecord.stat.setCversion(newCversion);
        addChangeRecord(parentRecord);
        addChangeRecord(new ChangeRecord(request.getHdr().getZxid(), path, s, 0, listACL));
    }

    private void validatePath(String path, long sessionId) throws BadArgumentsException {
        try {
            PathUtils.validatePath(path);
        } catch (IllegalArgumentException ie) {
            LOG.info("Invalid path {} with session 0x{}, reason: {}",
                    path, Long.toHexString(sessionId), ie.getMessage());
            throw new BadArgumentsException(path);
        }
    }

    private String getParentPathAndValidate(String path)
            throws BadArgumentsException {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1 || path.indexOf('\0') != -1
                || zks.getZKDatabase().isSpecialPath(path)) {
            throw new BadArgumentsException(path);
        }
        return path.substring(0, lastSlash);
    }

    /**
     * This method will be called inside the ProcessRequestThread, which is a
     * singleton, so there will be a single thread calling this code.
     *
     * @param request
     */
    protected void pRequest(Request request) throws RequestProcessorException {
        // LOG.info("Prep>>> cxid = " + request.cxid + " type = " +
        // request.type + " id = 0x" + Long.toHexString(request.sessionId));
        request.setHdr(null);
        request.setTxn(null);

        try {
            switch (request.op) {
                case createContainer:
                case create:
                case create2:
                    CreateRequest create2Request = new CreateRequest();
                    pRequest2Txn(request.op, zks.getNextZxid(), request, create2Request, true);
                    break;
                case createTTL:
                    CreateTTLRequest createTtlRequest = new CreateTTLRequest();
                    pRequest2Txn(request.op, zks.getNextZxid(), request, createTtlRequest, true);
                    break;
                case deleteContainer:
                case delete:
                    DeleteRequest deleteRequest = new DeleteRequest();
                    pRequest2Txn(request.op, zks.getNextZxid(), request, deleteRequest, true);
                    break;
                case setData:
                    SetDataRequest setDataRequest = new SetDataRequest();
                    pRequest2Txn(request.op, zks.getNextZxid(), request, setDataRequest, true);
                    break;
                case reconfig:
                    ReconfigRequest reconfigRequest = new ReconfigRequest();
                    ByteBufferInputStream.byteBuffer2Record(request.request, reconfigRequest);
                    pRequest2Txn(request.op, zks.getNextZxid(), request, reconfigRequest, true);
                    break;
                case setACL:
                    SetACLRequest setAclRequest = new SetACLRequest();
                    pRequest2Txn(request.op, zks.getNextZxid(), request, setAclRequest, true);
                    break;
                case check:
                    CheckVersionRequest checkRequest = new CheckVersionRequest();
                    pRequest2Txn(request.op, zks.getNextZxid(), request, checkRequest, true);
                    break;
                case multi:
                    MultiTransactionRecord multiRequest = new MultiTransactionRecord();
                    try {
                        ByteBufferInputStream.byteBuffer2Record(request.request, multiRequest);
                    } catch (IOException e) {
                        request.setHdr(new TxnHeader(request.sessionId, request.cxid, zks.getNextZxid(),
                                Time.currentWallTime(), OpType.multi.getValue()));
                        throw e;
                    }
                    List<Txn> txns = new ArrayList<Txn>();
                    //Each op in a multi-op must have the same zxid!
                    long zxid = zks.getNextZxid();
                    KeeperException ke = null;

                    //Store off current pending change records in case we need to rollback
                    Map<String, ChangeRecord> pendingChanges = getPendingChanges(multiRequest);

                    for (Op op : multiRequest) {
                        Record subrequest = op.toRequestRecord();
                        OpType opCode;
                        Record txn;

                        /* If we've already failed one of the ops, don't bother
                         * trying the rest as we know it's going to fail and it
                         * would be confusing in the logfiles.
                         */
                        if (ke != null) {
                            opCode = OpType.error;
                            txn = new ErrorTxn(KECode.RUNTIMEINCONSISTENCY.intValue());
                        }

                        /* Prep the request and convert to a Txn */
                        else {
                            try {
                                pRequest2Txn(op.getOpType(), zxid, request, subrequest, false);
                                opCode = OpType.getOpCode(request.getHdr().getType());
                                txn = request.getTxn();
                            } catch (KeeperException e) {
                                ke = e;
                                opCode = OpType.error;
                                txn = new ErrorTxn(e.code().intValue());

                                LOG.info("Got user-level KeeperException when processing "
                                        + request.toString() + " aborting remaining multi ops."
                                        + " Error Path:" + e.getPath()
                                        + " Error:" + e.getMessage());

                                request.setException(e);

                                /* Rollback change records from failed multi-op */
                                rollbackPendingChanges(zxid, pendingChanges);
                            }
                        }

                        //FIXME: I don't want to have to serialize it here and then
                        //       immediately deserialize in next processor. But I'm
                        //       not sure how else to get the txn stored into our list.
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        BinaryOutputArchive boa = BinaryOutputArchive.getArchive(baos);
                        txn.serialize(boa, "request");
                        ByteBuffer bb = ByteBuffer.wrap(baos.toByteArray());

                        txns.add(new Txn(opCode.getValue(), bb.array()));
                    }

                    request.setHdr(new TxnHeader(request.sessionId, request.cxid, zxid,
                            Time.currentWallTime(), request.op.getValue()));
                    request.setTxn(new MultiTxn(txns));

                    break;

                //create/close session don't require request record
                case createSession:
                case closeSession:
                    if (!request.isLocalSession()) {
                        pRequest2Txn(request.op, zks.getNextZxid(), request,
                                null, true);
                    }
                    break;

                //All the rest don't need to create a Txn - just verify session
                case sync:
                case exists:
                case getData:
                case getACL:
                case getChildren:
                case getChildren2:
                case ping:
                case setWatches:
                case checkWatches:
                case removeWatches:
                    zks.getSessionTracker().checkSession(request.sessionId,
                            request.getOwner());
                    break;
                default:
                    LOG.warn("unknown op " + request.op);
                    break;
            }
        } catch (KeeperException e) {
            if (request.getHdr() != null) {
                request.getHdr().setType(OpType.error.getValue());
                request.setTxn(new ErrorTxn(e.code().intValue()));
            }
            LOG.info("Got user-level KeeperException when processing "
                    + request.toString()
                    + " Error Path:" + e.getPath()
                    + " Error:" + e.getMessage());
            request.setException(e);
        } catch (Exception e) {
            // log at error level as we are returning a marshalling
            // error to the user
            LOG.error("Failed to process " + request, e);

            StringBuilder sb = new StringBuilder();
            ByteBuffer bb = request.request;
            if (bb != null) {
                bb.rewind();
                while (bb.hasRemaining()) {
                    sb.append(Integer.toHexString(bb.get() & 0xff));
                }
            } else {
                sb.append("request buffer is null");
            }

            LOG.error("Dumping request buffer: 0x" + sb.toString());
            if (request.getHdr() != null) {
                request.getHdr().setType(OpType.error.getValue());
                request.setTxn(new ErrorTxn(KeeperException.KECode.MARSHALLINGERROR.intValue()));
            }
        }
        request.zxid = zks.getZxid();
        nextProcessor.processRequest(request);
    }

    private List<ACL> removeDuplicates(List<ACL> acl) {

        LinkedList<ACL> retval = new LinkedList<ACL>();
        if (acl != null) {
            for (ACL a : acl) {
                if (!retval.contains(a)) {
                    retval.add(a);
                }
            }
        }
        return retval;
    }

    private void validateCreateRequest(String path, CreateMode createMode, Request request, long ttl)
            throws KeeperException {
        try {
            EphemeralType.validateTTL(createMode, ttl);
        } catch (IllegalArgumentException e) {
            throw new BadArgumentsException(path);
        }
        if (createMode.isEphemeral()) {
            // Exception is set when local session failed to upgrade
            // so we just need to report the error
            if (request.getException() != null) {
                throw request.getException();
            }
            zks.getSessionTracker().checkGlobalSession(request.sessionId,
                    request.getOwner());
        } else {
            zks.getSessionTracker().checkSession(request.sessionId,
                    request.getOwner());
        }
    }

    /**
     * This method checks out the acl making sure it isn't null or empty,
     * it has valid schemes and ids, and expanding any relative ids that
     * depend on the requestor's authentication information.
     *
     * @param authInfo list of ACL IDs associated with the client connection
     * @param acls     list of ACLs being assigned to the node (create or setACL operation)
     * @return verified and expanded ACLs
     * @throws KeeperException.InvalidACLException
     */
    private List<ACL> fixupACL(String path, List<Id> authInfo, List<ACL> acls)
            throws KeeperException.InvalidACLException {
        // check for well formed ACLs
        // This resolves https://issues.apache.org/jira/browse/ZOOKEEPER-1877
        List<ACL> uniqacls = removeDuplicates(acls);
        LinkedList<ACL> rv = new LinkedList<ACL>();
        if (uniqacls == null || uniqacls.size() == 0) {
            throw new KeeperException.InvalidACLException(path);
        }
        for (ACL a : uniqacls) {
            LOG.debug("Processing ACL: {}", a);
            if (a == null) {
                throw new KeeperException.InvalidACLException(path);
            }
            Id id = a.getId();
            if (id == null || id.getScheme() == null) {
                throw new KeeperException.InvalidACLException(path);
            }
            if (id.getScheme().equals("world") && id.getId().equals("anyone")) {
                rv.add(a);
            } else if (id.getScheme().equals("auth")) {
                // This is the "auth" id, so we have to expand it to the
                // authenticated ids of the requestor
                boolean authIdValid = false;
                for (Id cid : authInfo) {
                    ServerAuthenticationProvider ap =
                            ProviderRegistry.getServerProvider(cid.getScheme());
                    if (ap == null) {
                        LOG.error("Missing AuthenticationProvider for "
                                + cid.getScheme());
                    } else if (ap.isAuthenticated()) {
                        authIdValid = true;
                        rv.add(new ACL(a.getPerms(), cid));
                    }
                }
                if (!authIdValid) {
                    throw new KeeperException.InvalidACLException(path);
                }
            } else {
                ServerAuthenticationProvider ap = ProviderRegistry.getServerProvider(id.getScheme());
                if (ap == null || !ap.isValid(id.getId())) {
                    throw new KeeperException.InvalidACLException(path);
                }
                rv.add(a);
            }
        }
        return rv;
    }

    public void processRequest(Request request) {
        submittedRequests.add(request);
    }

    public void shutdown() {
        LOG.info("Shutting down");
        submittedRequests.clear();
        submittedRequests.add(Request.requestOfDeath);
        nextProcessor.shutdown();
    }

    private interface Fun {
        ChangeRecord apply();
    }
}
