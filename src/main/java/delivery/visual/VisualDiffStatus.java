package delivery.visual;

public enum VisualDiffStatus {
    /** No baseline.png on disk — AE does not auto-seed; Backend/FE approve seeds. */
    NO_BASELINE,
    PASS,
    FAIL,
    /** Missing actual bytes or I/O error. */
    ERROR
}
