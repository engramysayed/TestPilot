package delivery.job;

public final class JobAdmissionException extends IllegalStateException {
    private final String code;

    public JobAdmissionException(String code, String reason) {
        super(reason == null ? "job not admitted" : reason);
        this.code = code == null ? "QUEUE_SATURATED" : code;
    }

    public String code() {
        return code;
    }
}
