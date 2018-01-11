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

import java.io.Flushable;
import java.io.IOException;

import org.apache.zookeeper.server.quorum.QuorumPacket;
import org.apache.zookeeper.server.quorum.roles.OpOfLeader;
import org.apache.zookeeper.server.quorum.roles.learner.Learner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.processor.RequestProcessor;

public class SendAckRequestProcessor implements RequestProcessor, Flushable {
    private static final Logger LOG = LoggerFactory.getLogger(SendAckRequestProcessor.class);

    private Learner learner;

    public SendAckRequestProcessor(Learner peer) {
        this.learner = peer;
    }

    public void processRequest(Request si) {
        if(si.op != OpType.sync){
            QuorumPacket qp = new QuorumPacket(OpOfLeader.ACK.intType(), si.getHdr().getZxid(), null,
                null);
            writeOrCloseSocket(qp, false);
        }
    }

    public void flush() throws IOException {
        writeOrCloseSocket(null, true);
    }

    private void writeOrCloseSocket(QuorumPacket qp, boolean flush) {
        try {
            learner.writePacket(qp, false);
        } catch (IOException e) {
            LOG.warn("Closing connection to leader, exception during packet send", e);
            try {
                if (!learner.getSocket().isClosed()) {
                    learner.getSocket().close();
                }
            } catch (IOException e1) {
                // Nothing to do, we are shutting things down, so an exception here is irrelevant
                LOG.debug("Ignoring error closing the connection", e1);
            }
        }
    }

    public void shutdown() {
        // Nothing needed
    }

}
