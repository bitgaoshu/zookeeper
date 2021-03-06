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

package org.apache.zookeeper.server.quorum.processor;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.zookeeper.exception.KeeperException.KECode;
import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.proto.ReplyHeader;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.exception.RequestProcessorException;
import org.apache.zookeeper.server.zkThread.ZooKeeperCriticalThread;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.util.ZooTrace;
import org.apache.zookeeper.server.processor.RequestProcessor;
import org.apache.zookeeper.server.quorum.roles.leader.Leader.XidRolloverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This processor is at the beginning of the ReadOnlyZooKeeperServer's
 * processors chain. All it does is, it passes read-only operations (e.g.
 * OpType.getData, OpType.exists) through to the next processor, but drops
 * state-changing operations (e.g. OpType.create, OpType.setData).
 */
public class ReadOnlyRequestProcessor extends ZooKeeperCriticalThread implements
        RequestProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyRequestProcessor.class);

    private final LinkedBlockingQueue<Request> queuedRequests = new LinkedBlockingQueue<Request>();

    private boolean finished = false;

    private final RequestProcessor nextProcessor;

    private final ZooKeeperServer zks;

    public ReadOnlyRequestProcessor(ZooKeeperServer zks,
            RequestProcessor nextProcessor) {
        super("ReadOnlyRequestProcessor:" + zks.getServerId(), zks
                .getZooKeeperServerListener());
        this.zks = zks;
        this.nextProcessor = nextProcessor;
    }

    public void run() {
        try {
            while (!finished) {
                Request request = queuedRequests.take();

                // log request
                long traceMask = ZooTrace.CLIENT_REQUEST_TRACE_MASK;
                if (request.op == OpType.ping) {
                    traceMask = ZooTrace.CLIENT_PING_TRACE_MASK;
                }
                if (LOG.isTraceEnabled()) {
                    ZooTrace.logRequest(LOG, traceMask, 'R', request, "");
                }
                if (Request.requestOfDeath == request) {
                    break;
                }

                // filter read requests
                switch (request.op) {
                case sync:
                case create:
                case create2:
                case createTTL:
                case createContainer:
                case delete:
                case deleteContainer:
                case setData:
                case reconfig:
                case setACL:
                case multi:
                case check:
                    ReplyHeader hdr = new ReplyHeader(request.cxid, zks.getZKDatabase()
                            .getDataTreeLastProcessedZxid(), KECode.NOTREADONLY.intValue());
                    try {
                        request.cnxn.sendResponse(hdr, null, null);
                    } catch (IOException e) {
                        LOG.error("IO exception while sending response", e);
                    }
                    continue;
                }

                // proceed to the next processor
                if (nextProcessor != null) {
                    nextProcessor.processRequest(request);
                }
            }
        } catch (RequestProcessorException e) {
            if (e.getCause() instanceof XidRolloverException) {
                LOG.info(e.getCause().getMessage());
            }
            handleException(this.getName(), e);
        } catch (Exception e) {
            handleException(this.getName(), e);
        }
        LOG.info("ReadOnlyRequestProcessor exited loop!");
    }

    @Override
    public void processRequest(Request request) {
        if (!finished) {
            queuedRequests.add(request);
        }
    }

    @Override
    public void shutdown() {
        finished = true;
        queuedRequests.clear();
        queuedRequests.add(Request.requestOfDeath);
        nextProcessor.shutdown();
    }

}
