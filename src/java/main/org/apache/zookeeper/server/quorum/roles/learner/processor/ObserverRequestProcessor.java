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

package org.apache.zookeeper.server.quorum.roles.learner.processor;

import org.apache.zookeeper.exception.KeeperException;
import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.processor.RequestProcessor;
import org.apache.zookeeper.server.zkThread.ZooKeeperCriticalThread;
import org.apache.zookeeper.server.util.ZooTrace;
import org.apache.zookeeper.server.quorum.roles.learner.server.ObserverZooKeeperServer;
import org.apache.zookeeper.txn.ErrorTxn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This RequestProcessor forwards any requests that modify the state of the
 * system to the leader.
 */
public class ObserverRequestProcessor extends ZooKeeperCriticalThread implements
        RequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ObserverRequestProcessor.class);

    ObserverZooKeeperServer zks;

    RequestProcessor nextProcessor;

    // We keep a queue of requests. As requests get submitted they are
    // stored here. The queue is drained in the run() method.
    LinkedBlockingQueue<Request> queuedRequests = new LinkedBlockingQueue<Request>();

    boolean finished = false;

    /**
     * Constructor - takes an ObserverZooKeeperServer to associate with
     * and the next processor to pass requests to after we're finished.
     * @param zks
     * @param nextProcessor
     */
    public ObserverRequestProcessor(ObserverZooKeeperServer zks,
            RequestProcessor nextProcessor) {
        super("ObserverRequestProcessor:" + zks.getServerId(), zks
                .getZooKeeperServerListener());
        this.zks = zks;
        this.nextProcessor = nextProcessor;
    }

    @Override
    public void run() {
        try {
            while (!finished) {
                Request request = queuedRequests.take();
                if (LOG.isTraceEnabled()) {
                    ZooTrace.logRequest(LOG, ZooTrace.CLIENT_REQUEST_TRACE_MASK,
                            'F', request, "");
                }
                if (request == Request.requestOfDeath) {
                    break;
                }
                // We want to queue the request to be processed before we submit
                // the request to the leader so that we are ready to receive
                // the response
                nextProcessor.processRequest(request);

                // We now ship the request to the leader. As with all
                // other quorum operations, sync also follows this code
                // path, but different from others, we need to keep track
                // of the sync operations this Observer has pending, so we
                // add it to pendingSyncs.
                switch (request.op) {
                    case sync:
                        zks.pendingSyncRequest(request);
                        zks.getObserver().request(request);
                        break;
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
                        zks.getObserver().request(request);
                        break;
                    case createSession:
                    case closeSession:
                    // Don't forward local sessions to the leader.
                    if (!request.isLocalSession()) {
                        zks.getObserver().request(request);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            handleException(this.getName(), e);
        }
        LOG.info("ObserverRequestProcessor exited loop!");
    }

    /**
     * Simply queue the request, which will be processed in FIFO order.
     */
    public void processRequest(Request request) {
        if (!finished) {
            Request upgradeRequest = null;
            try {
                upgradeRequest = zks.checkUpgradeSession(request);
            } catch (KeeperException ke) {
                if (request.getHdr() != null) {
                    request.getHdr().setType(OpType.error.getValue());
                    request.setTxn(new ErrorTxn(ke.code().intValue()));
                }
                request.setException(ke);
                LOG.info("Error creating upgrade request",  ke);
            } catch (IOException ie) {
                LOG.error("Unexpected error in upgrade", ie);
            }
            if (upgradeRequest != null) {
                queuedRequests.add(upgradeRequest);
            }
            queuedRequests.add(request);
        }
    }

    /**
     * Shutdown the processor.
     */
    public void shutdown() {
        LOG.info("Shutting down");
        finished = true;
        queuedRequests.clear();
        queuedRequests.add(Request.requestOfDeath);
        nextProcessor.shutdown();
    }

}
