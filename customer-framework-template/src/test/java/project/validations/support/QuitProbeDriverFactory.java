package project.validations.support;

/** Counts quit calls so teardown can prove cleanup after assertAll throws. */
public final class QuitProbeDriverFactory {

    public static int quitCalls;

    private QuitProbeDriverFactory() {
    }

    public static void quit() {
        quitCalls++;
    }
}
