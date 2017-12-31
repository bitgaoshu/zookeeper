package org.apache.zookeeper.common;

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.zookeeper.ZooDefs;

import java.util.HashMap;
import java.util.Map;

@InterfaceAudience.Public
public enum OpCode {

    notification(0),
    create(1),
    delete(2),
    exists(3),
    getData(4),
    setData(5),
    getACL(6),
    setACL(7),
    getChildren(8),
    sync(9),
    ping(11),
    getChildren2(12),
    check(13),
    multi(14),
    create2(15),
    reconfig(16),
    checkWatches(17),
    removeWatches(18),
    createContainer(19),
    deleteContainer(20),
    createTTL(21),
    auth(100),
    setWatches(101),
    sasl(102),
    createSession(-10),
    closeSession(-11),
    error(-1),
    noDefine(-2);
    private int value;

    OpCode(int val) {
        this.value = val;
    }

    private static Map<Integer, OpCode> codes = new HashMap<>();
    static {
        for (OpCode opCode : OpCode.values()) {
            codes.put(opCode.getValue(), opCode);
        }
    }


    public int getValue() {
        return value;
    }

    public static OpCode getOpCode(int val) {
        return codes.getOrDefault(val, noDefine);
    }

    /**
     * is the packet op a valid packet in zookeeper
     *
     * @param op
     *                the op of the packet
     * @return true if a valid packet, false if not
     */
    public static boolean isValid(OpCode op) {
        // make sure this is always synchronized with Zoodefs!!
        switch (op) {
            case notification:
                return false;
            case check:
            case closeSession:
            case create:
            case create2:
            case createTTL:
            case createContainer:
            case createSession:
            case delete:
            case deleteContainer:
            case exists:
            case getACL:
            case getChildren:
            case getChildren2:
            case getData:
            case multi:
            case ping:
            case reconfig:
            case setACL:
            case setData:
            case setWatches:
            case sync:
            case checkWatches:
            case removeWatches:
                return true;
            default:
                return false;
        }
    }

    public static boolean isQuorum(OpCode op, boolean isLocalSession) {
        switch (op) {
            case exists:
            case getACL:
            case getChildren:
            case getChildren2:
            case getData:
                return false;
            case create:
            case create2:
            case createTTL:
            case createContainer:
            case error:
            case delete:
            case deleteContainer:
            case setACL:
            case setData:
            case check:
            case multi:
            case reconfig:
                return true;
            case closeSession:
            case createSession:
                return !isLocalSession;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return "OpCode{" +
                "value=" + value + "\n" +
                "name=" + name() + "\n" +
                '}';
    }
}
