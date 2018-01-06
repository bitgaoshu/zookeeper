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

package org.apache.zookeeper.util;

import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Provide insight into the runtime environment.
 *
 */
public class LogEnv {
    public static final String JAAS_CONF_KEY = "java.security.auth.login.config";
    

    private static void put(Map<String, String> map, String k, String v) {
        map.put(k,v);
    }

    public static Map<String, String> map() {
        Map<String, String> map = new HashMap<>();
        put(map, "zookeeper.version", Version.getFullVersion());

        try {
            put(map, "host.name",
                InetAddress.getLocalHost().getCanonicalHostName());
        } catch (UnknownHostException e) {
            put(map, "host.name", "<NA>");
        }

        put(map, "java.version",
                System.getProperty("java.version", "<NA>"));
        put(map, "java.vendor",
                System.getProperty("java.vendor", "<NA>"));
        put(map, "java.home",
                System.getProperty("java.home", "<NA>"));
        put(map, "java.class.path",
                System.getProperty("java.class.path", "<NA>"));
        put(map, "java.library.path",
                System.getProperty("java.library.path", "<NA>"));
        put(map, "java.io.tmpdir",
                System.getProperty("java.io.tmpdir", "<NA>"));
        put(map, "java.compiler",
                System.getProperty("java.compiler", "<NA>"));
        put(map, "os.name",
                System.getProperty("os.name", "<NA>"));
        put(map, "os.arch",
                System.getProperty("os.arch", "<NA>"));
        put(map, "os.version",
                System.getProperty("os.version", "<NA>"));
        put(map, "user.name",
                System.getProperty("user.name", "<NA>"));
        put(map, "user.home",
                System.getProperty("user.home", "<NA>"));
        put(map, "user.dir",
                System.getProperty("user.dir", "<NA>"));

        // Get memory information.
        Runtime runtime = Runtime.getRuntime();
        int mb = 1024 * 1024;
        put(map, "os.memory.free",
               Long.toString(runtime.freeMemory() / mb) + "MB");
        put(map, "os.memory.max",
               Long.toString(runtime.maxMemory() / mb) + "MB");
        put(map, "os.memory.total",
               Long.toString(runtime.totalMemory() / mb) + "MB");

        return map;
    }
    
    public static void logEnv(String msg, Logger log) {
        for (Map.Entry<String, String> entry : LogEnv.map().entrySet()) {
            log.info(msg + entry.toString());
        }

    }
}
