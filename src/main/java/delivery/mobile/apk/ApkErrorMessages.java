package delivery.mobile.apk;

/**
 * Plain-language APK upload/inspect messages for portal JSON (Frontend COORDINATE_WITH_BACKEND.md).
 */
public final class ApkErrorMessages {
    private ApkErrorMessages() {
    }

    public static String plain(ApkErrorCode code) {
        if (code == null) {
            return "That file doesn’t look like a valid Android app package.";
        }
        return switch (code) {
            case ACTIVITY_REQUIRED -> "Choose which screen should open when the app starts.";
            case APK_INVALID -> "That file doesn’t look like a valid Android app package.";
            case APP_ID_MISMATCH ->
                    "This APK belongs to a different app than the one already saved for this project.";
            case OVERSIZED -> "This APK is too large to upload.";
            case UNSAFE_NAME -> "That file name isn’t allowed. Rename the APK and try again.";
            case REMOTE_SOURCE_REJECTED ->
                    "Remote download URLs aren’t accepted — upload the APK file from your computer.";
            case API_INCOMPATIBLE ->
                    "This APK needs a newer Android version than your phones support.";
            case ABI_INCOMPATIBLE -> "This APK doesn’t match the CPU type of your phones.";
            case DOWNGRADE_REJECTED -> "This APK is an older version than the one already saved.";
            case SIGNATURE_REPLACEMENT_REJECTED ->
                    "This APK is signed differently from the version already saved.";
            case INSPECTION_FAILED -> "That file doesn’t look like a valid Android app package.";
        };
    }

    public static String plainOrFallback(ApkErrorCode code, String technical) {
        String msg = plain(code);
        if (technical != null && !technical.isBlank()
                && (code == ApkErrorCode.APK_INVALID || code == ApkErrorCode.INSPECTION_FAILED)
                && technical.toLowerCase().contains("unknown or expired")) {
            return technical;
        }
        return msg;
    }
}
