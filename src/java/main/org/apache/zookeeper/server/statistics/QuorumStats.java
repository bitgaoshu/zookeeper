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

package org.apache.zookeeper.server.statistics;

import org.apache.zookeeper.server.quorum.QuorumState;

public interface QuorumStats {

    String[] getQuorumPeers();
    QuorumState getServerState();

    default String statsInfo() {
        StringBuilder sb=new StringBuilder(this.toString());
        QuorumState state=getServerState();
        switch (state) {
            case LEADING:{
                sb.append("Followers:");
                for(String f: getQuorumPeers()){
                    sb.append(" ").append(f);
                }
                sb.append("\n");
                break;
            }
            case FOLLOWING:
            case OBSERVING:{
                sb.append("leader: ");
                String[] ldr=getQuorumPeers();
                if(ldr.length>0)
                    sb.append(ldr[0]);
                else
                    sb.append("not connected");
                sb.append("\n");
                break;
            }

        }
        return sb.toString();
    }
}
