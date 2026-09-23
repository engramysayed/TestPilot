package delivery.vision;



public interface GroundingBrowser {

    GroundedNode elementFromPoint(double cssX, double cssY);



    byte[] screenshotPng();



    ViewportMetrics metrics();



    void scrollViewport();

    default boolean matchesObservedNode(String strategy, String value) { return true; }
    default String observationVersion() { return ""; }
    default Object saveScroll() { return null; }
    default void restoreScroll(Object position) {}

}


