package org.apache.zookeeper.server.quorum;

public enum ServerState {
    LOOKING,
    FOLLOWING,
    LEADING,
    OBSERVING;
}
