/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.client.cliCmds;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.PosixParser;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.exception.ConfigException;
import org.apache.zookeeper.exception.KeeperException;
import org.apache.zookeeper.server.quorum.QuorumPeer;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

/**
 * get config command for cli
 */
public class GetConfigCommand extends CliCommand {

    private static Options options = new Options();
    private String args[];
    private CommandLine cl;

    {
        options.addOption("s", false, "stats");
        options.addOption("w", false, "watcher");
        options.addOption("c", false, "client connection string");
    }

    public GetConfigCommand() {
        super("config", "[-c] [-w] [-s]");
    }

    private static String getClientConfigStr(String configData) {
        Properties props = new Properties();
        try {
            props.load(new StringReader(configData));
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
        StringBuffer sb = new StringBuffer();
        String version = "";
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = entry.getKey().toString().trim();
            String value = entry.getValue().toString().trim();
            if (key.equals("version")) version = value;
            if (!key.startsWith("server.")) continue;
            sb.append(value).append(";");
//            QuorumPeer.QuorumServer qs;
//            try {
//                qs = new QuorumPeer.QuorumServer(-1, value);
//            } catch (ConfigException e) {
//                e.printStackTrace();
//                continue;
//            }
//            if (!first) sb.append(",");
//            else first = false;
//            if (null != qs.clientAddr) {
//                sb.append(qs.clientAddr.getHostString()
//                        + ":" + qs.clientAddr.getPort());
//            }
        }
        return version + " " + sb.toString();
    }

    @Override
    public CliCommand parse(String[] cmdArgs) throws CliParseException {

        Parser parser = new PosixParser();
        try {
            cl = parser.parse(options, cmdArgs);
        } catch (ParseException ex) {
            throw new CliParseException(ex);
        }
        args = cl.getArgs();
        if (args.length < 1) {
            throw new CliParseException(getUsageStr());
        }

        return this;
    }

    @Override
    public boolean exec() throws CliException {
        boolean watch = cl.hasOption("w");
        Stat stat = new Stat();
        byte data[];
        try {
            data = zk.getConfig(watch, stat);
        } catch (KeeperException | InterruptedException ex) {
            throw new CliWrapperException(ex);
        }
        data = (data == null) ? "null".getBytes() : data;
        if (cl.hasOption("c")) {
            out.println(getClientConfigStr(new String(data)));
        } else {
            out.println(new String(data));
        }

        if (cl.hasOption("s")) {
            new StatPrinter(out).print(stat);
        }

        return watch;
    }
}
