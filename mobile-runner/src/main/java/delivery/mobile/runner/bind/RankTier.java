package delivery.mobile.runner.bind;

/**
 * Lower ordinal = higher priority. User-validated overrides sit above automatic hierarchy.
 * label-sibling is below resource-id and above relative xpath (arch §5.3).
 */
public enum RankTier {
    USER_OVERRIDE,   // 0 — validated user locator
    RESOURCE_ID,     // 1
    ACCESSIBILITY,   // 2
    LABEL_SIBLING,   // 3
    TEXT_CLASS,      // 4
    XPATH,           // 5
    OTHER            // 6
}
