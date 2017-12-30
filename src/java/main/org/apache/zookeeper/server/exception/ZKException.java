package org.apache.zookeeper.server.exception;

import java.io.IOException;

public class ZKException {
    public static class CloseRequestException extends IOException {
        private static final long serialVersionUID = -7854505709816442681L;

        public CloseRequestException(String msg) {
            super(msg);
        }
    }
}
