package org.apache.zookeeper.server.quorum;

import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.quorum.roles.leader.SyncedLearnerTracker;

public class Proposal extends SyncedLearnerTracker {
    public QuorumPacket packet;
    public Request request;

    @Override
    public String toString() {
        return packet.getType() + ", " + packet.getZxid() + ", " + request;
    }
}
