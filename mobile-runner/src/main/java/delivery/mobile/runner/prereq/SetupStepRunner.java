package delivery.mobile.runner.prereq;

import delivery.mobile.prereq.SetupPlanStep;

/** Executes one setup plan step (M3-03). */
public interface SetupStepRunner {
    /** @return true if step succeeded */
    boolean run(SetupPlanStep step);
}
