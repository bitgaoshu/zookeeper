package org.apache.zookeeper.server.quorum;

import org.apache.zookeeper.server.quorum.QuorumPeer;

public interface Role {

    QuorumPeer getQuorumPeer();
}
