package org.apache.zookeeper.server.processor;

import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.StatPersisted;
import org.apache.zookeeper.server.DataTree;

import java.util.ArrayList;
import java.util.List;

/**
 * This structure is used to facilitate information sharing between PrepRP
 * and FinalRP.
 */
public class ChangeRecord {
    final long zxid;
    final String path;
    final StatPersisted stat; /* Make sure to create a new object when changing */
    final List<ACL> acl; /* Make sure to create a new object when changing */
    int childCount;

    ChangeRecord(long zxid, String path, StatPersisted stat, int childCount,
                 List<ACL> acl) {
        this.zxid = zxid;
        this.path = path;
        this.stat = stat;
        this.childCount = childCount;
        this.acl = acl;
    }

    ChangeRecord duplicate(long zxid) {
        StatPersisted stat = new StatPersisted();
        if (this.stat != null) {
            DataTree.copyStatPersisted(this.stat, stat);
        }
        return new ChangeRecord(zxid, path, stat, childCount,
                acl == null ? new ArrayList<ACL>() : new ArrayList<ACL>(acl));
    }
}
