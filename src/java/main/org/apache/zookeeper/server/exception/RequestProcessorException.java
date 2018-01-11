package org.apache.zookeeper.server.exception;

@SuppressWarnings("serial")
public class RequestProcessorException extends Exception {
    public RequestProcessorException(String msg, Throwable t) {
        super(msg, t);
    }
}
