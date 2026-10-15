package delivery.mobile.runner.webview;

/** Checks a Chromedriver the operator already installed. Never downloads one. */
public final class ChromedriverQualification {
    private ChromedriverQualification() {}

    public record Request(
            String requiredVersion,
            String observedVersion,
            String approvedPath,
            boolean pathExists,
            boolean runtimeDownload
    ) {}

    public record Result(boolean ok, String code, String approvedPath) {}

    public static Result check(Request request) {
        if (request.runtimeDownload()) {
            return new Result(false, "RUNTIME_DOWNLOAD_BLOCKED", "");
        }
        if (request.approvedPath() == null || request.approvedPath().isBlank() || !request.pathExists()) {
            return new Result(false, "CHROMEDRIVER_PATH", "");
        }
        String required = request.requiredVersion() == null ? "" : request.requiredVersion();
        String observed = request.observedVersion() == null ? "" : request.observedVersion();
        if (!required.equals(observed)) {
            return new Result(false, "CHROMEDRIVER_MISMATCH", "");
        }
        return new Result(true, "", request.approvedPath());
    }
}
