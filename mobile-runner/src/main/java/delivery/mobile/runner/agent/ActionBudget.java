package delivery.mobile.runner.agent;

/** Shared count of taps, types, and checks. Zero means the next action must not start. */
public final class ActionBudget {

    private int left;

    public ActionBudget(int actions) {
        this.left = Math.max(0, actions);
    }

    public boolean exhausted() {
        return left <= 0;
    }

    /** @return true when this action may start */
    public boolean take() {
        if (left <= 0) {
            return false;
        }
        left--;
        return true;
    }
}
