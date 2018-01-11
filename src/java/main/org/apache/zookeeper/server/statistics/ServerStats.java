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

package org.apache.zookeeper.server.statistics;


import org.apache.zookeeper.server.common.Time;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Basic Server Statistics
 */
public class ServerStats {
    private final Provider provider;
    private AtomicLong packetsSent = new AtomicLong(0);
    private AtomicLong packetsReceived = new AtomicLong(0);
    private long maxLatency;
    private long minLatency = Long.MAX_VALUE;
    private long totalLatency = 0;
    private long count = 0;

    public ServerStats(Provider provider) {
        this.provider = provider;
    }

    // getters
    synchronized public long getMinLatency() {
        return minLatency == Long.MAX_VALUE ? 0 : minLatency;
    }

    synchronized public long getAvgLatency() {
        if (count != 0) {
            return totalLatency / count;
        }
        return 0;
    }

    synchronized public long getMaxLatency() {
        return maxLatency;
    }

    public long getOutstandingRequests() {
        return provider.getOutstandingRequests();
    }

    public long getLastProcessedZxid() {
        return provider.getLastProcessedZxid();
    }

    public long getPacketsReceived() {
        return packetsReceived.get();
    }

    public long getPacketsSent() {
        return packetsSent.get();
    }

    public String getServerState() {
        return provider.getState();
    }

    /** The number of client connections alive to this server */
    public int getNumAliveClientConnections() {
        return provider.getNumAliveConnections();
    }

    public boolean isProviderNull() {
        return provider == null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Latency min/avg/max: " + getMinLatency() + "/"
                + getAvgLatency() + "/" + getMaxLatency() + "\n");
        sb.append("Received: " + getPacketsReceived() + "\n");
        sb.append("Sent: " + getPacketsSent() + "\n");
        sb.append("Connections: " + getNumAliveClientConnections() + "\n");

        if (provider != null) {
            sb.append("Outstanding: " + getOutstandingRequests() + "\n");
            sb.append("Zxid: 0x" + Long.toHexString(getLastProcessedZxid()) + "\n");
        }
        sb.append("Mode: " + getServerState() + "\n");
        return sb.toString();
    }

    // mutators
    synchronized public void updateLatency(long requestCreateTime) {
        long latency = Time.currentElapsedTime() - requestCreateTime;
        totalLatency += latency;
        count++;
        if (latency < minLatency) {
            minLatency = latency;
        }
        if (latency > maxLatency) {
            maxLatency = latency;
        }
    }

    synchronized public void resetLatency() {
        totalLatency = 0;
        count = 0;
        maxLatency = 0;
        minLatency = Long.MAX_VALUE;
    }

    synchronized public void resetMaxLatency() {
        maxLatency = getMinLatency();
    }

    public void incrementPacketsReceived() {
        packetsReceived.incrementAndGet();
    }

    public void incrementPacketsSent() {
        packetsSent.incrementAndGet();
    }

    public void resetRequestCounters() {
        packetsReceived.set(0L);
        packetsSent.set(0L);
    }

    synchronized public void reset() {
        resetLatency();
        resetRequestCounters();
    }

    public interface Provider {
        long getOutstandingRequests();

        long getLastProcessedZxid();

        String getState();

        int getNumAliveConnections();

        long getDataDirSize();

        long getLogDirSize();
    }

}
