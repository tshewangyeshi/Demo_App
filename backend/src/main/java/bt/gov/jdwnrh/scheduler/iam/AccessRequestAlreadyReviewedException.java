package bt.gov.jdwnrh.scheduler.iam;

/** Thrown by StaffAccessRequestService when approve()/reject() is called on a request that isn't PENDING anymore. */
public class AccessRequestAlreadyReviewedException extends RuntimeException {
    public AccessRequestAlreadyReviewedException(String message) {
        super(message);
    }
}
