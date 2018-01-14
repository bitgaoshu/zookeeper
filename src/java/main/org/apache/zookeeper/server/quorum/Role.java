package org.apache.zookeeper.server.quorum;

import org.apache.zookeeper.server.ZooKeeperServer;

public interface Role {

    QuorumPeer getQuorumPeer();

    ZooKeeperServer getZk();
}
