package org.apache.zookeeper.watcher;

import org.apache.yetus.audience.InterfaceAudience;

/**
 * Enumeration of types of watchers
 */
@InterfaceAudience.Public
public enum WatcherType {
    Children(1), Data(2), Any(3);

    // Integer representation of value
    private final int intValue;

    private WatcherType(int intValue) {
        this.intValue = intValue;
    }

    public int getIntValue() {
        return intValue;
    }

    public static WatcherType fromInt(int intValue) {
        switch (intValue) {
        case 1:
            return WatcherType.Children;
        case 2:
            return WatcherType.Data;
        case 3:
            return WatcherType.Any;

        default:
            throw new RuntimeException(
                    "Invalid integer value for conversion to WatcherType");
        }
    }
}
