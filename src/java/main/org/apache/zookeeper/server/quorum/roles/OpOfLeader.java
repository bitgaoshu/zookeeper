package org.apache.zookeeper.server.quorum.roles;

import org.omg.CORBA.PUBLIC_MEMBER;

import java.util.Map;

public enum OpOfLeader{
    /**
     * This message is the first that a follower receives from the leader.
     * It has the protocol version and the epoch of the leader.
     */
    LEADERINFO(17, "LEADERINFO"),
    /**
     * This message is used by the follow to ack a proposed epoch.
     */
    ACKEPOCH(18, "ACKEPOCH"),
    /**
     * This message type is sent by a leader to propose a mutation.
     */
    PROPOSAL(2, "PROPOSAL"),
    /**
     * This message is for follower to expect diff
     */
    DIFF(13, "DIFF"),
    /**
     * This is for follower to truncate its logs
     */
    TRUNC(14, "TRUNC"),
    /**
     * This is for follower to download the snapshots
     */
    SNAP(15,"SNAP"),
    /**
     * This tells the leader that the connecting peer is actually an observer
     */
    OBSERVERINFO(16,"OBSERVERINFO"),
    /**
     * This message type is sent by the leader to indicate it's zxid and if
     * needed, its database.
     */
    NEWLEADER(10, "NEWLEADER"),
    /**
     * This message type is sent by a follower to pass the last zxid. This is here
     * for backward compatibility purposes.
     */
    FOLLOWERINFO(11, "FOLLOWERINFO"),
    /**
     * This message type is sent by the leader to indicate that the follower is
     * now uptodate andt can start responding to clients.
     */
    UPTODATE(12,"UPTODATE"),
    /**
     * This message type is sent to a leader to request and mutation operation.
     * The payload will consist of a request header followed by a request.
     */
    REQUEST(1,"REQUEST"),
    /**
     * This message type is sent by a follower after it has synced a proposal.
     */
    ACK(3, "ACK"),
    /**
     * This message type is sent by a leader to commit a proposal and cause
     * followers to start serving the corresponding data.
     */
    COMMIT(4, "COMMIT"),
    /**
     * This message type is enchanged between follower and leader (initiated by
     * follower) to determine liveliness.
     */
    PING(5, "PING"),
    /**
     * This message type is to validate a session that should be active.
     */
    REVALIDATE(6,"REVALIDATE"),
    /**
     * This message is a reply to a synchronize cmd4l flushing the pipe
     * between the leader and the follower.
     */
    SYNC(7,"SYNC"),
    /**
     * This message type informs observers of a committed proposal.
     */
    INFORM(8, "INFORM"),
    /**
     * Similar to COMMIT, only for a reconfig operation.
     */
    COMMITANDACTIVATE(9,"COMMITANDACTIVATE"),
    /**
     * Similar to INFORM, only for a reconfig operation.
     */
    INFORMANDACTIVATE(19,"INFORMANDACTIVATE"),

    UNKONWNOP(-1, "UNKONWNOP");
    private static Map<Integer, OpOfLeader> maps;
    static {
        for( OpOfLeader op : values()) {
            maps.put(op.type, op);
        }
    }
    private int type;
    private String msg;
    OpOfLeader(int type, String msg){
        this.type = type;
        this.msg = msg;
    }
    public int intType() {
        return type;
    }
    public String msg() {
        return msg;
    }
    public static OpOfLeader fromInt(int type) {
        return maps.getOrDefault(type, UNKONWNOP);
    }
}
