package org.apache.zookeeper.server.quorum.roles;

import org.apache.zookeeper.server.quorum.QuorumPeer;

public interface Role {

    QuorumPeer getQuorumPeer();
}
