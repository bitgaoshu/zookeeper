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

package org.apache.zookeeper.server;

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.zookeeper.server.admin.AdminServer;
import org.apache.zookeeper.server.admin.AdminServer.AdminServerException;
import org.apache.zookeeper.server.admin.AdminServerFactory;
import org.apache.zookeeper.server.cnxn.ServerCnxnFactory;
import org.apache.zookeeper.server.jmx.ManagedUtil;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog.DatadirException;
import org.apache.zookeeper.exception.ConfigException;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.JMException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class starts and runs a standalone ZooKeeperServer.
 */
@InterfaceAudience.Public
public class ZKServerStandAloneMain {
    private static final Logger LOG =
        LoggerFactory.getLogger(ZKServerStandAloneMain.class);

    private static final String USAGE =
        "Usage: ZKServerStandAloneMain configfile | port datadir [ticktime] [maxcnxns]";

    // ZooKeeper server supports two kinds of connection: unencrypted and encrypted.
    private ServerCnxnFactory cnxnFactory;
    private ServerCnxnFactory secureCnxnFactory;
    private ContainerManager containerManager;

    private AdminServer adminServer;

    /*
     * Start up the ZooKeeper server.
     *
     * @param args the configfile or the port datadir [ticktime]
     */
    public static void main(String[] args) {
        ZKServerStandAloneMain main = new ZKServerStandAloneMain();
        try {
            main.initializeAndRun(args);
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid arguments, exiting abnormally", e);
            LOG.info(USAGE);
            System.err.println(USAGE);
            System.exit(2);
        } catch (ConfigException e) {
            LOG.error("Invalid config, exiting abnormally", e);
            System.err.println("Invalid config, exiting abnormally");
            System.exit(2);
        } catch (DatadirException e) {
            LOG.error("Unable to access datadir, exiting abnormally", e);
            System.err.println("Unable to access datadir, exiting abnormally");
            System.exit(3);
        } catch (AdminServerException e) {
            LOG.error("Unable to start AdminServer, exiting abnormally", e);
            System.err.println("Unable to start AdminServer, exiting abnormally");
            System.exit(4);
        } catch (Exception e) {
            LOG.error("Unexpected exception, exiting abnormally", e);
            System.exit(1);
        }
        LOG.info("Exiting normally");
        System.exit(0);
    }

    protected void initializeAndRun(String[] args)
        throws ConfigException, IOException, AdminServerException
    {
        try {
            ManagedUtil.registerLog4jMBeans();
        } catch (JMException e) {
            LOG.warn("Unable to register log4j JMX control", e);
        }

        ServerConfig config = new ServerConfig();
        if (args.length == 1) {
            config.parse(args[0]);
        } else {
            config.parse(args);
        }

        runFromConfig(config);
    }

    /**
     * Run from a ServerConfig.
     * @param config ServerConfig to use.
     * @throws IOException
     * @throws AdminServerException
     */
    public void runFromConfig(ServerConfig config)
            throws IOException, AdminServerException {
        LOG.info("Starting server");
        FileTxnSnapLog txnLog = null;
        try {
            // Note that this thread isn't going to be doing anything else,
            // so rather than spawning another thread, we will just call
            // run() in this thread.
            // create a file logger url from the command line args
            txnLog = new FileTxnSnapLog(config.dataLogDir, config.dataDir);
            final ZooKeeperServer zkServer = new ZooKeeperServer(txnLog,
                    config.tickTime, config.minSessionTimeout, config.maxSessionTimeout, null);

            // Registers shutdown handler which will be used to know the
            // server error or shutdown state changes.
            final CountDownLatch shutdownLatch = new CountDownLatch(1);
            zkServer.registerServerShutdownHandler(
                    new ZooKeeperServerShutdownHandler(shutdownLatch));

            // Start Admin server
            adminServer = AdminServerFactory.createAdminServer();
            adminServer.setZooKeeperServer(zkServer);
            adminServer.start();

            boolean needStartZKServer = true;
            if (config.getClientPortAddress() != null) {
                cnxnFactory = ServerCnxnFactory.createFactory();
                cnxnFactory.configure(config.getClientPortAddress(), config.getMaxClientCnxns(), false);
                cnxnFactory.startup(zkServer);
                // zkServer has been started. So we don't need to start it again in secureCnxnFactory.
                needStartZKServer = false;
            }
            if (config.getSecureClientPortAddress() != null) {
                secureCnxnFactory = ServerCnxnFactory.createFactory();
                secureCnxnFactory.configure(config.getSecureClientPortAddress(), config.getMaxClientCnxns(), true);
                secureCnxnFactory.startup(zkServer, needStartZKServer);
            }

            containerManager = new ContainerManager(zkServer.getZKDatabase(), zkServer.firstProcessor,
                    Integer.getInteger("znode.container.checkIntervalMs", (int) TimeUnit.MINUTES.toMillis(1)),
                    Integer.getInteger("znode.container.maxPerMinute", 10000)
            );
            containerManager.start();

            // Watch status of ZooKeeper server. It will do a graceful shutdown
            // if the server is not running or hits an internal error.
            shutdownLatch.await();

            shutdown();

            if (cnxnFactory != null) {
                cnxnFactory.join();
            }
            if (secureCnxnFactory != null) {
                secureCnxnFactory.join();
            }
            if (zkServer.canShutdown()) {
                zkServer.shutdown(true);
            }
        } catch (InterruptedException e) {
            // warn, but generally this is ok
            LOG.warn("Server interrupted", e);
        } finally {
            if (txnLog != null) {
                txnLog.close();
            }
        }
    }

    /**
     * Shutdown the serving instance
     */
    protected void shutdown() {
        if (containerManager != null) {
            containerManager.stop();
        }
        if (cnxnFactory != null) {
            cnxnFactory.shutdown();
        }
        if (secureCnxnFactory != null) {
            secureCnxnFactory.shutdown();
        }
        try {
            if (adminServer != null) {
                adminServer.shutdown();
            }
        } catch (AdminServerException e) {
            LOG.warn("Problem stopping AdminServer", e);
        }
    }

    // VisibleForTesting
    ServerCnxnFactory getCnxnFactory() {
        return cnxnFactory;
    }

    /**
     * Server configuration storage.
     *
     * We use this instead of Properties as it's typed.
     *
     */
    @InterfaceAudience.Public
    public static class ServerConfig {
        ////
        //// If you update the configuration parameters be sure
        //// to update the "conf" 4letter word
        ////
        protected InetSocketAddress clientPortAddress;
        protected InetSocketAddress secureClientPortAddress;
        protected File dataDir;
        protected File dataLogDir;
        protected int tickTime = ZooKeeperServer.DEFAULT_TICK_TIME;
        protected int maxClientCnxns;
        /** defaults to -1 if not set explicitly */
        protected int minSessionTimeout = -1;
        /** defaults to -1 if not set explicitly */
        protected int maxSessionTimeout = -1;

        /**
         * Parse arguments for server configuration
         * @param args clientPort dataDir and optional tickTime and maxClientCnxns
         * @return ServerConfig configured wrt arguments
         * @throws IllegalArgumentException on invalid usage
         */
        public void parse(String[] args) {
            if (args.length < 2 || args.length > 4) {
                throw new IllegalArgumentException("Invalid number of arguments:" + Arrays.toString(args));
            }

            clientPortAddress = new InetSocketAddress(Integer.parseInt(args[0]));
            dataDir = new File(args[1]);
            dataLogDir = dataDir;
            if (args.length >= 3) {
                tickTime = Integer.parseInt(args[2]);
            }
            if (args.length == 4) {
                maxClientCnxns = Integer.parseInt(args[3]);
            }
        }

        /**
         * Parse a ZooKeeper configuration file
         * @param path the patch of the configuration file
         * @return ServerConfig configured wrt arguments
         * @throws ConfigException error processing configuration
         */
        public void parse(String path) throws ConfigException {
            QuorumPeerConfig config = new QuorumPeerConfig();
            config.parse(path);

            // let qpconfig parse the file and then pull the stuff we are
            // interested in
            readFrom(config);
        }

        /**
         * Read attributes from a QuorumPeerConfig.
         * @param config
         */
        public void readFrom(QuorumPeerConfig config) {
            clientPortAddress = config.getClientPortAddress();
            secureClientPortAddress = config.getSecureClientPortAddress();
            dataDir = config.getDataDir();
            dataLogDir = config.getDataLogDir();
            tickTime = config.getTickTime();
            maxClientCnxns = config.getMaxClientCnxns();
            minSessionTimeout = config.getMinSessionTimeout();
            maxSessionTimeout = config.getMaxSessionTimeout();
        }

        public InetSocketAddress getClientPortAddress() {
            return clientPortAddress;
        }
        public InetSocketAddress getSecureClientPortAddress() {
            return secureClientPortAddress;
        }
        public File getDataDir() { return dataDir; }
        public File getDataLogDir() { return dataLogDir; }
        public int getTickTime() { return tickTime; }
        public int getMaxClientCnxns() { return maxClientCnxns; }
        /** minimum session timeout in milliseconds, -1 if unset */
        public int getMinSessionTimeout() { return minSessionTimeout; }
        /** maximum session timeout in milliseconds, -1 if unset */
        public int getMaxSessionTimeout() { return maxSessionTimeout; }
    }
}
