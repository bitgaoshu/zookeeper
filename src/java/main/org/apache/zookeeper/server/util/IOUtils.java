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
package org.apache.zookeeper.server.util;

import org.apache.jute.BinaryInputArchive;
import org.apache.jute.InputArchive;
import org.apache.jute.OutputArchive;
import org.apache.jute.Record;
import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.server.persistence.DataTree;
import org.apache.zookeeper.server.quorum.QuorumPacket;
import org.apache.zookeeper.server.quorum.roles.OpOfLeader;
import org.apache.zookeeper.txn.CreateContainerTxn;
import org.apache.zookeeper.txn.CreateSessionTxn;
import org.apache.zookeeper.txn.CreateTTLTxn;
import org.apache.zookeeper.txn.CreateTxn;
import org.apache.zookeeper.txn.CreateTxnV0;
import org.apache.zookeeper.txn.DeleteTxn;
import org.apache.zookeeper.txn.ErrorTxn;
import org.apache.zookeeper.txn.MultiTxn;
import org.apache.zookeeper.txn.SetACLTxn;
import org.apache.zookeeper.txn.SetDataTxn;
import org.apache.zookeeper.txn.TxnHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/*
 * This code is originally from HDFS, see the similarly named files there
 * in case of bug fixing, history, etc...
 */

public class IOUtils {

    private static final Logger LOG = LoggerFactory.getLogger(IOUtils.class);

    /**
     * Closes the stream ignoring {@link IOException}. Must only be called in
     * cleaning up from exception handlers.
     *
     * @param stream
     *            the Stream to close
     */
    public static void closeStream(Closeable stream) {
        cleanup(null, stream);
    }

    /**
     * Close the Closeable objects and <b>ignore</b> any {@link IOException} or
     * null pointers. Must only be used for cleanup in exception handlers.
     *
     * @param log
     *            the log to record problems to at debug level. Can be null.
     * @param closeables
     *            the objects to close
     */
    public static void cleanup(Logger log, Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    if (log != null) {
                        log.warn("Exception in closing " + c, e);
                    }
                }
            }
        }
    }

    /**
     * Copies from one stream to another.
     *
     * @param in
     *            InputStrem to read from
     * @param out
     *            OutputStream to write to
     * @param buffSize
     *            the size of the buffer
     * @param close
     *            whether or not close the InputStream and OutputStream at the
     *            end. The streams are closed in the finally clause.
     */
    public static void copyBytes(InputStream in, OutputStream out,
                                 int buffSize, boolean close) throws IOException {
        try {
            copyBytes(in, out, buffSize);
            if (close) {
                out.close();
                out = null;
                in.close();
                in = null;
            }
        } finally {
            if (close) {
                closeStream(out);
                closeStream(in);
            }
        }
    }

    /**
     * Copies from one stream to another.
     *
     * @param in
     *            InputStrem to read from
     * @param out
     *            OutputStream to write to
     * @param buffSize
     *            the size of the buffer
     */
    public static void copyBytes(InputStream in, OutputStream out, int buffSize)
            throws IOException {
        PrintStream ps = out instanceof PrintStream ? (PrintStream) out : null;
        byte buf[] = new byte[buffSize];
        int bytesRead = in.read(buf);
        while (bytesRead >= 0) {
            out.write(buf, 0, bytesRead);
            if ((ps != null) && ps.checkError()) {
                throw new IOException("Unable to write to output stream.");
            }
            bytesRead = in.read(buf);
        }
    }

    public static Record deserializeTxn(byte txnBytes[], TxnHeader hdr)
            throws IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(txnBytes);
        InputArchive ia = BinaryInputArchive.getArchive(bais);

        hdr.deserialize(ia, "hdr");
        bais.mark(bais.available());
        Record txn = null;
        OpType op = OpType.getOpCode(hdr.getType());
        switch (op) {
            case createSession:
                // This isn't really an error txn; it just has the same
                // format. The error represents the timeout
                txn = new CreateSessionTxn();
                break;
            case closeSession:
                return null;
            case create:
            case create2:
                txn = new CreateTxn();
                break;
            case createTTL:
                txn = new CreateTTLTxn();
                break;
            case createContainer:
                txn = new CreateContainerTxn();
                break;
            case delete:
            case deleteContainer:
                txn = new DeleteTxn();
                break;
            case reconfig:
            case setData:
                txn = new SetDataTxn();
                break;
            case setACL:
                txn = new SetACLTxn();
                break;
            case error:
                txn = new ErrorTxn();
                break;
            case multi:
                txn = new MultiTxn();
                break;
            default:
                throw new IOException("Unsupported Txn with type=%d" + hdr.getType());
        }
        if (txn != null) {
            try {
                txn.deserialize(ia, "txn");
            } catch (EOFException e) {
                // perhaps this is a V0 Create
                if (op == OpType.create) {
                    CreateTxn create = (CreateTxn) txn;
                    bais.reset();
                    CreateTxnV0 createv0 = new CreateTxnV0();
                    createv0.deserialize(ia, "txn");
                    // cool now make it V1. a -1 parentCVersion will
                    // trigger fixup processing in processTxn
                    create.setPath(createv0.getPath());
                    create.setData(createv0.getData());
                    create.setAcl(createv0.getAcl());
                    create.setEphemeral(createv0.getEphemeral());
                    create.setParentCVersion(-1);
                } else {
                    throw e;
                }
            }
        }
        return txn;
    }

    public static void deserializeSnapshot(DataTree dt, InputArchive ia,
                                           Map<Long, Integer> sessions) throws IOException {
        int count = ia.readInt("count");
        while (count > 0) {
            long id = ia.readLong("id");
            int to = ia.readInt("timeout");
            sessions.put(id, to);
            if (LOG.isTraceEnabled()) {
                ZooTrace.logTraceMessage(LOG, ZooTrace.SESSION_TRACE_MASK,
                        "loadData --- session in archive: " + id
                                + " with timeout: " + to);
            }
            count--;
        }
        dt.deserialize(ia, "tree");
    }

    public static void serializeSnapshot(DataTree dt, OutputArchive oa,
                                         Map<Long, Integer> sessions) throws IOException {
        HashMap<Long, Integer> sessSnap = new HashMap<Long, Integer>(sessions);
        oa.writeInt(sessSnap.size(), "count");
        for (Map.Entry<Long, Integer> entry : sessSnap.entrySet()) {
            oa.writeLong(entry.getKey().longValue(), "id");
            oa.writeInt(entry.getValue().intValue(), "timeout");
        }
        dt.serialize(oa, "tree");
    }

    public static String serializePacket2String(QuorumPacket qp) {
        String mess = null;
        OpOfLeader op = OpOfLeader.fromInt(qp.getType());

        switch (op) {
            case PROPOSAL:
                TxnHeader hdr = new TxnHeader();
                try {
                    deserializeTxn(qp.getData(), hdr);
                    // mess = "transaction: " + txn.toString();
                } catch (IOException e) {
                    LOG.warn("Unexpected exception", e);
                }
                break;
            case REVALIDATE:
                ByteArrayInputStream bis = new ByteArrayInputStream(qp.getData());
                DataInputStream dis = new DataInputStream(bis);
                try {
                    long id = dis.readLong();
                    mess = " sessionid = " + id;
                } catch (IOException e) {
                    LOG.warn("Unexpected exception", e);
                }
        }
        return op.msg() + " " + Long.toHexString(qp.getZxid()) + " " + mess;
    }
}
