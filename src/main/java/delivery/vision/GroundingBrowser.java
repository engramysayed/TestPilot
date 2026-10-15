package delivery.vision;



public interface GroundingBrowser {

    GroundedNode elementFromPoint(double cssX, double cssY);

    /**
     * Same probe, with the name the step quotes. The live browser uses it to return a label
     * that is not a button, link, or menu row. Fakes keep the two-argument probe.
     */
    default GroundedNode elementFromPoint(double cssX, double cssY, String namedText) {
        return elementFromPoint(cssX, cssY);
    }



    byte[] screenshotPng();



    ViewportMetrics metrics();



    void scrollViewport();

    default boolean matchesObservedNode(String strategy, String value) { return true; }
    default boolean hasObservationIdentity() { return false; }
    default String observationVersion() { return ""; }
    default Object saveScroll() { return null; }
    default void restoreScroll(Object position) {}

}


