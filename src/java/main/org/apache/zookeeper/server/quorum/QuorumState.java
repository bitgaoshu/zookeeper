package org.apache.zookeeper.server.quorum;

public enum QuorumState {
    LOOKING("leaderelection"),
    FOLLOWING("following"),
    LEADING("leading"),
    OBSERVING("observing");

    private String msg;
    QuorumState(String msg){
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }
}
