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

package org.apache.zookeeper.server.session;

import org.apache.zookeeper.ZKTestCase;
import org.apache.zookeeper.exception.KeeperException;
import org.apache.zookeeper.operation.OpType;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.processor.PrepRequestProcessor;
import org.apache.zookeeper.server.processor.RequestProcessor;
import org.apache.zookeeper.server.session.SessionTrackerImpl.SessionImpl;
import org.apache.zookeeper.test.ClientBase;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Testing zk client session logic in sessiontracker
 */
public class SessionTrackerTest extends ZKTestCase {

    private final long sessionId = 339900;
    private final int sessionTimeout = 3000;
    private FirstProcessor firstProcessor;
    private CountDownLatch latch;

    /**
     * Verify the create session call in the leader.FinalRequestProcessor after
     * the session expiration.
     */
    @Test(timeout = 20000)
    public void testAddSessionAfterSessionExpiry() throws Exception {
        ZooKeeperServer zks = setupSessionTracker();

        latch = new CountDownLatch(1);
        zks.getSessionTracker().addSession(sessionId, sessionTimeout);
        SessionTrackerImpl sessionTrackerImpl = (SessionTrackerImpl) zks.getSessionTracker();
        SessionImpl sessionImpl = sessionTrackerImpl.sessionsById
                .get(sessionId);
        Assert.assertNotNull("Sessionid:" + sessionId
                + " doesn't exists in sessiontracker", sessionImpl);

        // verify the session existence
        Object sessionOwner = new Object();
        sessionTrackerImpl.checkSession(sessionId, sessionOwner);

        // waiting for the session expiry
        latch.await(sessionTimeout * 2, TimeUnit.MILLISECONDS);

        // Simulating FinalRequestProcessor logic: create session request has
        // delayed and now reaches FinalRequestProcessor. Here the leader zk
        // will do sessionTracker.addSession(id, timeout)
        sessionTrackerImpl.addSession(sessionId, sessionTimeout);
        try {
            sessionTrackerImpl.checkSession(sessionId, sessionOwner);
            Assert.fail("Should throw session expiry exception "
                    + "as the session has expired and closed");
        } catch (KeeperException.SessionExpiredException e) {
            // expected behaviour
        }
        Assert.assertTrue("Session didn't expired", sessionImpl.isClosing());
        Assert.assertFalse("Session didn't expired", sessionTrackerImpl
                .touchSession(sessionId, sessionTimeout));
        Assert.assertEquals(
                "Duplicate session expiry request has been generated", 1,
                firstProcessor.getCountOfCloseSessionReq());
    }

    /**
     * Verify the session closure request has reached PrepRequestProcessor soon
     * after session expiration by the session tracker
     */
    @Test(timeout = 20000)
    public void testCloseSessionRequestAfterSessionExpiry() throws Exception {
        ZooKeeperServer zks = setupSessionTracker();

        latch = new CountDownLatch(1);
        zks.getSessionTracker().addSession(sessionId, sessionTimeout);
        SessionTrackerImpl sessionTrackerImpl = (SessionTrackerImpl) zks.getSessionTracker();
        SessionImpl sessionImpl = sessionTrackerImpl.sessionsById
                .get(sessionId);
        Assert.assertNotNull("Sessionid:" + sessionId
                + " doesn't exists in sessiontracker", sessionImpl);

        // verify the session existence
        Object sessionOwner = new Object();
        sessionTrackerImpl.checkSession(sessionId, sessionOwner);

        // waiting for the session expiry
        latch.await(sessionTimeout * 2, TimeUnit.MILLISECONDS);

        // Simulating close session request: removeSession() will be executed
        // while OpType.closeSession
        sessionTrackerImpl.removeSession(sessionId);
        SessionImpl actualSession = sessionTrackerImpl.sessionsById
                .get(sessionId);
        Assert.assertNull("Session:" + sessionId
                + " still exists after removal", actualSession);
    }

    private ZooKeeperServer setupSessionTracker() throws IOException {
        File tmpDir = ClientBase.createTmpDir();
        ClientBase.setupTestEnv();
        MyZookeeper zks = new MyZookeeper(tmpDir, tmpDir, 3000);
        zks.setupRequestProcessors();
        firstProcessor = new FirstProcessor(zks, null);
        zks.setFirstProcessor(firstProcessor);

        // setup session tracker
        zks.createSessionTracker();
        zks.startSessionTracker();
        return zks;
    }


    private static class MyZookeeper extends ZooKeeperServer {

        MyZookeeper(File snapDir, File logDir, int tickTime) throws IOException {
            super(snapDir, logDir, tickTime);
        }

        @Override
        public void setupRequestProcessors() {

        }

        @Override
        public void createSessionTracker() {
            super.createSessionTracker();
        }

        @Override
        public void startSessionTracker() {
            super.startSessionTracker();
        }

        public void setFirstProcessor(RequestProcessor firstProcessor) {
            this.firstProcessor = firstProcessor;
        }

    }

    // Mock processor used in zookeeper server
    private class FirstProcessor extends PrepRequestProcessor {
        private volatile int countOfCloseSessionReq = 0;

        public FirstProcessor(ZooKeeperServer zks,
                              RequestProcessor nextProcessor) {
            super(zks, nextProcessor);
        }

        @Override
        public void processRequest(Request request) {
            // check session close request
            if (request.op == OpType.closeSession) {
                countOfCloseSessionReq++;
                latch.countDown();
            }
        }

        // return number of session expiry calls
        int getCountOfCloseSessionReq() {
            return countOfCloseSessionReq;
        }
    }
}
