package delivery.portal.service.credits;

/** Job gate: balance &lt; cost. Mapped to HTTP 402 with code INSUFFICIENT_CREDITS. */
public final class InsufficientCreditsException extends RuntimeException {
    public static final String CODE = "INSUFFICIENT_CREDITS";

    private final long required;
    private final long balance;
    private final String jobKind;

    public InsufficientCreditsException(long required, long balance, String jobKind) {
        this(required, balance, jobKind, null);
    }

    public InsufficientCreditsException(long required, long balance, String jobKind, String breakdown) {
        super("Not enough credits to start this job. You need " + required
                + (breakdown == null || breakdown.isBlank() ? "" : " (" + breakdown + ")")
                + "; you have " + balance + ". Ask your Keel admin to add credits after payment.");
        this.required = required;
        this.balance = balance;
        this.jobKind = jobKind == null ? "" : jobKind;
    }

    public long required() { return required; }
    public long balance() { return balance; }
    public String jobKind() { return jobKind; }
}
