package delivery.mobile.prereq;

/** Ordered setup closure phases (architecture §7 / M3-03). */
public enum SetupPhaseKind {
    APP_BASELINE,
    AUTH_POLICY,
    START_STATE,
    PREREQUISITE,
    LEAF
}
