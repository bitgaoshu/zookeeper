package org.apache.zookeeper.server.quorum;

public enum QuorumState {
    LOOKING,
    FOLLOWING,
    LEADING,
    OBSERVING;
}
