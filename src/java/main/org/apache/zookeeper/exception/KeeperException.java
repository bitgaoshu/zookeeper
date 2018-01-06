/*
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

package org.apache.zookeeper.exception;

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.zookeeper.operation.OpResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("serial")
@InterfaceAudience.Public
public abstract class KeeperException extends Exception {
    /**
     * All multi-requests that result in an exception retain the results
     * here so that it is possible to examine the problems in the catch
     * scope.  Non-multi requests will get a null if they try to access
     * these results.
     */
    private List<OpResult> results;
    private KECode code;
    private String path;

    public KeeperException(KECode code) {
        this.code = code;
    }

    KeeperException(KECode code, String path) {
        this.code = code;
        this.path = path;
    }

    /**
     * All non-specific keeper exceptions should be constructed via
     * this factory method in order to guarantee consistency in error
     * codes and such.  If you know the error code, then you should
     * construct the special purpose exception directly.  That will
     * allow you to have the most specific possible declarations of
     * what exceptions might actually be thrown.
     *
     * @param code The error code.
     * @param path The ZooKeeper path being operated on.
     * @return The specialized exception, presumably to be thrown by
     * the caller.
     */
    public static KeeperException create(KECode code, String path) {
        KeeperException r = code.exception();
        r.path = path;
        return r;
    }
    public static KeeperException create(KECode code) {
        return create(code, null);
    }

    /**
     * Read the error KECode for this exception
     *
     * @return the error KECode for this exception
     */
    public KECode code() {
        return code;
    }

    /**
     * Read the path for this exception
     *
     * @return the path associated with this error, null if none
     */
    public String getPath() {
        return path;
    }

    @Override
    public String getMessage() {
        if (path == null || path.isEmpty()) {
            return "KeeperErrorCode = " + code.msg;
        }
        return "KeeperErrorCode = " + code.msg + " for " + path;
    }

    public void setMultiResults(List<OpResult> results) {
        this.results = results;
    }

    /**
     * If this exception was thrown by a multi-request then the (partial) results
     * and error codes can be retrieved using this getter.
     *
     * @return A copy of the list of results from the operations in the multi-request.
     * @since 3.4.0
     */
    public List<OpResult> getResults() {
        return results != null ? new ArrayList<>(results) : null;
    }

    /**
     * Codes which represent the various KeeperException
     * types. This enum replaces the deprecated earlier static final int
     * constants. The old, deprecated, values are in "camel case" while the new
     * enum values are in all CAPS.
     */
    @InterfaceAudience.Public
    public enum KECode {
        /**
         * Everything is OK
         */
        OK(0, "ok", IllegalArgumentException.class),

        /**
         * System and server-side errors.
         * This is never thrown by the server, it shouldn't be used other than
         * to indicate a range. Specifically error codes greater than this
         * value, but lesser than {@link #APIERROR}, are system errors.
         */
        SYSTEMERROR(-1, "SystemError", SystemErrorException.class),

        /**
         * A runtime inconsistency was found
         */
        RUNTIMEINCONSISTENCY(-2, "RuntimeInconsistency", RuntimeInconsistencyException.class),
        /**
         * A data inconsistency was found
         */
        DATAINCONSISTENCY(-3, "DataInconsistency", DataInconsistencyException.class),
        /**
         * Connection to the server has been lost
         */
        CONNECTIONLOSS(-4, "ConnectinLoss", ConnectionLossException.class),
        /**
         * Error while marshalling or unmarshalling data
         */
        MARSHALLINGERROR(-5, "MarshallingError", MarshallingErrorException.class),
        /**
         * Operation is unimplemented
         */
        UNIMPLEMENTED(-6, "Unimplemented", UnimplementedException.class),
        /**
         * Operation timeout
         */
        OPERATIONTIMEOUT(-7, "OperationTimeout", OperationTimeoutException.class),
        /**
         * Invalid arguments
         */
        BADARGUMENTS(-8, "BadArguments", BadArgumentsException.class),
        /**
         * No quorum of new config is connected and up-to-date with the leader of last commmitted config - try
         * invoking reconfiguration after new servers are connected and synced
         */
        NEWCONFIGNOQUORUM(-13, "NewConfigNoQuorumException", NewConfigNoQuorumException.class),
        /**
         * Another reconfiguration is in progress -- concurrent reconfigs not supported (yet)
         */
        RECONFIGINPROGRESS(-14, "ReconfigInProcess", ReconfigInProgressException.class),
        /**
         * Unknown session (internal server use only)
         */
        UNKNOWNSESSION(-12, "UnkonwnSession", UnknownSessionException.class),

        /**
         * API errors.
         * This is never thrown by the server, it shouldn't be used other than
         * to indicate a range. Specifically error codes greater than this
         * value are API errors (while values less than this indicate a
         * {@link #SYSTEMERROR}).
         */
        APIERROR(-100, "APIError", APIErrorException.class),

        /**
         * Node does not exist
         */
        NONODE(-101, "NoNode", NoNodeException.class),
        /**
         * Not authenticated
         */
        NOAUTH(-102, "NoAuth", NoAuthException.class),
        /**
         * Version conflict
         * In case of reconfiguration: reconfig requested from config version X but last seen config has a different version Y
         */
        BADVERSION(-103, "BadVersion", BadArgumentsException.class),
        /**
         * Ephemeral nodes may not have children
         */
        NOCHILDRENFOREPHEMERALS(-108, "NoChildrenForEphemerals", NoChildrenForEphemeralsException.class),
        /**
         * The node already exists
         */
        NODEEXISTS(-110, "NodeExists", NodeExistsException.class),
        /**
         * The node has children
         */
        NOTEMPTY(-111, "Directory not empty", NotEmptyException.class),
        /**
         * The session has been expired by the server
         */
        SESSIONEXPIRED(-112, "Session expired", SessionExpiredException.class),
        /**
         * Invalid callback specified
         */
        INVALIDCALLBACK(-113, "Invalid callback", InvalidCallbackException.class),
        /**
         * Invalid ACL specified
         */
        INVALIDACL(-114, "InvalidACL", InvalidACLException.class),
        /**
         * Client authentication failed
         */
        AUTHFAILED(-115, "AuthFailed", AuthFailedException.class),
        /**
         * Session moved to another server, so operation is ignored
         */
        SESSIONMOVED(-118, "Session moved", SessionMovedException.class),
        /**
         * State-changing request is passed to read-only server
         */
        NOTREADONLY(-119, "Not a read-only call", NotReadOnlyException.class),
        /**
         * Attempt to create ephemeral node on a local session
         */
        EPHEMERALONLOCALSESSION(-120, "Ephemeral node on local session", EphemeralOnLocalSessionException.class),
        /**
         * Attempts to remove a non-existing watcher
         */
        NOWATCHER(-121, "No such watcher", NoWatcherException.class),
        /**
         * Attempts to perform a reconfiguration operation when reconfiguration feature is disabled.
         */
        RECONFIGDISABLED(-123, "Reconfig is disabled", ReconfigDisabledException.class);

        private static final Map<Integer, KECode> lookup
                = new HashMap<>();

        static {
            for (KECode c : EnumSet.allOf(KECode.class))
                lookup.put(c.code, c);
        }

        private final int code;
        private final String msg;
        private final Class exceptionClass;

        KECode(int code, String msg, Class exceptionClass) {
            this.code = code;
            this.msg = msg;
            this.exceptionClass = exceptionClass;
        }

        /**
         * Get the KECode value for a particular integer error code
         *
         * @param code int error code
         * @return KECode value corresponding to specified int code, or null
         */
        public static KECode get(int code) {
            return lookup.get(code);
        }

        /**
         * Get the int value for a particular KECode.
         *
         * @return error code as integer
         */
        public int intValue() {
            return code;
        }

        /**
         * Get the exception instance for a particular error code
         */
        public KeeperException exception() {
            KeeperException ke = null;
            try {
                ke = (KeeperException) exceptionClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
            return ke;
        }
    }

    /**
     * @see KECode#APIERROR
     */
    @InterfaceAudience.Public
    public static class APIErrorException extends KeeperException {
        public APIErrorException() {
            super(KECode.APIERROR);
        }
    }

    /**
     * @see KECode#AUTHFAILED
     */
    @InterfaceAudience.Public
    public static class AuthFailedException extends KeeperException {
        public AuthFailedException() {
            super(KECode.AUTHFAILED);
        }
    }

    /**
     * @see KECode#BADARGUMENTS
     */
    @InterfaceAudience.Public
    public static class BadArgumentsException extends KeeperException {
        public BadArgumentsException() {
            super(KECode.BADARGUMENTS);
        }

        public BadArgumentsException(String path) {
            super(KECode.BADARGUMENTS, path);
        }
    }

    /**
     * @see KECode#BADVERSION
     */
    @InterfaceAudience.Public
    public static class BadVersionException extends KeeperException {
        public BadVersionException() {
            super(KECode.BADVERSION);
        }

        public BadVersionException(String path) {
            super(KECode.BADVERSION, path);
        }
    }

    /**
     * @see KECode#CONNECTIONLOSS
     */
    @InterfaceAudience.Public
    public static class ConnectionLossException extends KeeperException {
        public ConnectionLossException() {
            super(KECode.CONNECTIONLOSS);
        }
    }

    /**
     * @see KECode#DATAINCONSISTENCY
     */
    @InterfaceAudience.Public
    public static class DataInconsistencyException extends KeeperException {
        public DataInconsistencyException() {
            super(KECode.DATAINCONSISTENCY);
        }
    }

    /**
     * @see KECode#INVALIDACL
     */
    @InterfaceAudience.Public
    public static class InvalidACLException extends KeeperException {
        public InvalidACLException() {
            super(KECode.INVALIDACL);
        }

        public InvalidACLException(String path) {
            super(KECode.INVALIDACL, path);
        }
    }

    /**
     * @see KECode#INVALIDCALLBACK
     */
    @InterfaceAudience.Public
    public static class InvalidCallbackException extends KeeperException {
        public InvalidCallbackException() {
            super(KECode.INVALIDCALLBACK);
        }
    }

    /**
     * @see KECode#MARSHALLINGERROR
     */
    @InterfaceAudience.Public
    public static class MarshallingErrorException extends KeeperException {
        public MarshallingErrorException() {
            super(KECode.MARSHALLINGERROR);
        }
    }

    /**
     * @see KECode#NOAUTH
     */
    @InterfaceAudience.Public
    public static class NoAuthException extends KeeperException {
        public NoAuthException() {
            super(KECode.NOAUTH);
        }
    }

    /**
     * @see KECode#NEWCONFIGNOQUORUM
     */
    @InterfaceAudience.Public
    public static class NewConfigNoQuorumException extends KeeperException {
        public NewConfigNoQuorumException() {
            super(KECode.NEWCONFIGNOQUORUM);
        }
    }

    /**
     * @see KECode#RECONFIGINPROGRESS
     */
    @InterfaceAudience.Public
    public static class ReconfigInProgressException extends KeeperException {
        public ReconfigInProgressException() {
            super(KECode.RECONFIGINPROGRESS);
        }
    }

    /**
     * @see KECode#NOCHILDRENFOREPHEMERALS
     */
    @InterfaceAudience.Public
    public static class NoChildrenForEphemeralsException extends KeeperException {
        public NoChildrenForEphemeralsException() {
            super(KECode.NOCHILDRENFOREPHEMERALS);
        }

        public NoChildrenForEphemeralsException(String path) {
            super(KECode.NOCHILDRENFOREPHEMERALS, path);
        }
    }

    /**
     * @see KECode#NODEEXISTS
     */
    @InterfaceAudience.Public
    public static class NodeExistsException extends KeeperException {
        public NodeExistsException() {
            super(KECode.NODEEXISTS);
        }

        public NodeExistsException(String path) {
            super(KECode.NODEEXISTS, path);
        }
    }

    /**
     * @see KECode#NONODE
     */
    @InterfaceAudience.Public
    public static class NoNodeException extends KeeperException {
        public NoNodeException() {
            super(KECode.NONODE);
        }

        public NoNodeException(String path) {
            super(KECode.NONODE, path);
        }
    }

    /**
     * @see KECode#NOTEMPTY
     */
    @InterfaceAudience.Public
    public static class NotEmptyException extends KeeperException {
        public NotEmptyException() {
            super(KECode.NOTEMPTY);
        }

        public NotEmptyException(String path) {
            super(KECode.NOTEMPTY, path);
        }
    }

    /**
     * @see KECode#OPERATIONTIMEOUT
     */
    @InterfaceAudience.Public
    public static class OperationTimeoutException extends KeeperException {
        public OperationTimeoutException() {
            super(KECode.OPERATIONTIMEOUT);
        }
    }

    /**
     * @see KECode#RUNTIMEINCONSISTENCY
     */
    @InterfaceAudience.Public
    public static class RuntimeInconsistencyException extends KeeperException {
        public RuntimeInconsistencyException() {
            super(KECode.RUNTIMEINCONSISTENCY);
        }
    }

    /**
     * @see KECode#SESSIONEXPIRED
     */
    @InterfaceAudience.Public
    public static class SessionExpiredException extends KeeperException {
        public SessionExpiredException() {
            super(KECode.SESSIONEXPIRED);
        }
    }

    /**
     * @see KECode#UNKNOWNSESSION
     */
    @InterfaceAudience.Public
    public static class UnknownSessionException extends KeeperException {
        public UnknownSessionException() {
            super(KECode.UNKNOWNSESSION);
        }
    }

    /**
     * @see KECode#SESSIONMOVED
     */
    @InterfaceAudience.Public
    public static class SessionMovedException extends KeeperException {
        public SessionMovedException() {
            super(KECode.SESSIONMOVED);
        }
    }

    /**
     * @see KECode#NOTREADONLY
     */
    @InterfaceAudience.Public
    public static class NotReadOnlyException extends KeeperException {
        public NotReadOnlyException() {
            super(KECode.NOTREADONLY);
        }
    }

    /**
     * @see KECode#EPHEMERALONLOCALSESSION
     */
    @InterfaceAudience.Public
    public static class EphemeralOnLocalSessionException extends KeeperException {
        public EphemeralOnLocalSessionException() {
            super(KECode.EPHEMERALONLOCALSESSION);
        }
    }

    /**
     * @see KECode#SYSTEMERROR
     */
    @InterfaceAudience.Public
    public static class SystemErrorException extends KeeperException {
        public SystemErrorException() {
            super(KECode.SYSTEMERROR);
        }
    }

    /**
     * @see KECode#UNIMPLEMENTED
     */
    @InterfaceAudience.Public
    public static class UnimplementedException extends KeeperException {
        public UnimplementedException() {
            super(KECode.UNIMPLEMENTED);
        }
    }

    /**
     * @see KECode#NOWATCHER
     */
    @InterfaceAudience.Public
    public static class NoWatcherException extends KeeperException {
        public NoWatcherException(String path) {
            super(KECode.NOWATCHER, path);
        }
    }

    /**
     * @see KECode#RECONFIGDISABLED
     */
    @InterfaceAudience.Public
    public static class ReconfigDisabledException extends KeeperException {
        public ReconfigDisabledException() {
            super(KECode.RECONFIGDISABLED);
        }
    }
}
