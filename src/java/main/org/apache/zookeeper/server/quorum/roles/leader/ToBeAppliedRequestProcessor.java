package org.apache.zookeeper.server.quorum.roles.leader;

import org.apache.zookeeper.server.exception.RequestProcessorException;
import org.apache.zookeeper.server.processor.FinalRequestProcessor;
import org.apache.zookeeper.server.Request;
import org.apache.zookeeper.server.processor.RequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToBeAppliedRequestProcessor implements RequestProcessor {
    private static final Logger log = LoggerFactory.getLogger(ToBeAppliedRequestProcessor.class);

    private final RequestProcessor next;

    private final Leader leader;

    /**
     * This request processor simply maintains the toBeApplied list. For
     * this to work next must be a FinalRequestProcessor and
     * FinalRequestProcessor.processRequest MUST process the request
     * synchronously!
     *
     * @param next a reference to the FinalRequestProcessor
     */
    public ToBeAppliedRequestProcessor(RequestProcessor next, Leader leader) {
        if (!(next instanceof FinalRequestProcessor)) {
            throw new RuntimeException(ToBeAppliedRequestProcessor.class
                    .getName()
                    + " must be connected to "
                    + FinalRequestProcessor.class.getName()
                    + " not "
                    + next.getClass().getName());
        }
        this.leader = leader;
        this.next = next;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.zookeeper.processor.RequestProcessor#processRequest(org.apache.zookeeper.processor.Request)
     */
    @Override
    public void processRequest(Request request) throws RequestProcessorException {
        next.processRequest(request);

        // The only requests that should be on toBeApplied are write
        // requests, for which we will have a hdr. We can't simply use
        // request.zxid here because that is set on read requests to equal
        // the zxid of the last write op.
        if (request.getHdr() != null) {
            leader.removeToAppliedQuest(request);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.zookeeper.processor.RequestProcessor#shutdown()
     */
    public void shutdown() {
        log.info("Shutting down");
        next.shutdown();
    }
}
