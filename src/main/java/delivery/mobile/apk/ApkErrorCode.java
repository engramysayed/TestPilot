package delivery.mobile.apk;

/** Portal-side APK inspection / upload rejection codes (contracts.md preflight/admission). */
public enum ApkErrorCode {
    APK_INVALID,
    APP_ID_MISMATCH,
    ACTIVITY_REQUIRED,
    /** Incompatible minSdk vs device API level. */
    API_INCOMPATIBLE,
    /** No overlapping ABI with the selected device. */
    ABI_INCOMPATIBLE,
    /** versionCode lower than prior revision without allowDowngrade. */
    DOWNGRADE_REJECTED,
    /** Signing certificate digest changed without allowSignatureReplacement. */
    SIGNATURE_REPLACEMENT_REJECTED,
    /** Remote URL or non-local APK source refused. */
    REMOTE_SOURCE_REJECTED,
    /** Uploaded archive exceeded configured size limit. */
    OVERSIZED,
    /** Path traversal or unsafe entry/file name. */
    UNSAFE_NAME,
    /** Tooling missing or inspection timed out (honest failure, not silent pass). */
    INSPECTION_FAILED
}
