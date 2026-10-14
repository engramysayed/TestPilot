package project.runtime.settle;

/** Post-settle effect classification (arch §6). NO_CHANGE is never PASS evidence. */
public enum ActionOutcome {
    CHANGED,
    CHANGED_VISUAL,
    NO_CHANGE
}
