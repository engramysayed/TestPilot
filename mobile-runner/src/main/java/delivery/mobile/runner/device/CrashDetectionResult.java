package delivery.mobile.runner.device;

public final class CrashDetectionResult {
    private final boolean appCrashed;
    private final String kind;
    private final String redactedExcerpt;
    private final String excerptRef;

    public CrashDetectionResult(boolean appCrashed, String kind, String redactedExcerpt, String excerptRef) {
        this.appCrashed = appCrashed;
        this.kind = kind == null ? "" : kind;
        this.redactedExcerpt = redactedExcerpt == null ? "" : redactedExcerpt;
        this.excerptRef = excerptRef == null ? "" : excerptRef;
    }

    public static CrashDetectionResult none() {
        return new CrashDetectionResult(false, "", "", "");
    }

    public boolean appCrashed() { return appCrashed; }
    public String kind() { return kind; }
    public String redactedExcerpt() { return redactedExcerpt; }
    public String excerptRef() { return excerptRef; }
}
