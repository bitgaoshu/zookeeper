/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server.quorum.election;

import org.apache.zookeeper.server.quorum.QuorumState;


class Vote {
    
    Vote(long id,
                    long zxid) {
        this.version = 0x0;
        this.id = id;
        this.zxid = zxid;
        this.electionEpoch = -1;
        this.peerEpoch = -1;
        this.state = QuorumState.LOOKING;
    }
    
    Vote(long id,
                    long zxid,
                    long peerEpoch) {
        this.version = 0x0;
        this.id = id;
        this.zxid = zxid;
        this.electionEpoch = -1;
        this.peerEpoch = peerEpoch;
        this.state = QuorumState.LOOKING;
    }

    Vote(long id,
                    long zxid,
                    long electionEpoch,
                    long peerEpoch) {
        this.version = 0x0;
        this.id = id;
        this.zxid = zxid;
        this.electionEpoch = electionEpoch;
        this.peerEpoch = peerEpoch;
        this.state = QuorumState.LOOKING;
    }
    
    Vote(int version,
                    long id,
                    long zxid,
                    long electionEpoch,
                    long peerEpoch,
                    QuorumState state) {
        this.version = version;
        this.id = id;
        this.zxid = zxid;
        this.electionEpoch = electionEpoch;
        this.state = state;
        this.peerEpoch = peerEpoch;
    }
    
    Vote(long id,
                    long zxid,
                    long electionEpoch,
                    long peerEpoch,
                    QuorumState state) {
        this.id = id;
        this.zxid = zxid;
        this.electionEpoch = electionEpoch;
        this.state = state;
        this.peerEpoch = peerEpoch;
        this.version = 0x0;
    }

    final private int version;

    final private long id;
    
    final private long zxid;
    
    final private long electionEpoch;
    
    final private long peerEpoch;
    
    int getVersion() {
        return version;
    }

    long getId() {
        return id;
    }

    long getZxid() {
        return zxid;
    }

    long getElectionEpoch() {
        return electionEpoch;
    }

    long getPeerEpoch() {
        return peerEpoch;
    }

    QuorumState getState() {
        return state;
    }

    final private QuorumState state;
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Vote)) {
            return false;
        }
        Vote other = (Vote) o;
        return (id == other.id
                    && zxid == other.zxid
                    && electionEpoch == other.electionEpoch
                    && peerEpoch == other.peerEpoch);

    }

    @Override
    public int hashCode() {
        return (int) (id & zxid);
    }

    @Override
    public String toString() {
        return "(" + id + ", " + Long.toHexString(zxid) + ", " + Long.toHexString(peerEpoch) + ")";
    }
}
