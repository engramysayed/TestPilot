package delivery.portal.record;

/** One headed browser session. Calls may block while the browser thread runs. */
public interface RecordingBrowser {
    String location();

    /** JSON from the page hook, or blank when the hook is not on the page yet. */
    String readEvents();

    void ensureInstalled();

    boolean alive();

    void close();
}
