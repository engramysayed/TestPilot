package delivery.vision;



public interface GroundingBrowser {

    GroundedNode elementFromPoint(double cssX, double cssY);



    byte[] screenshotPng();



    ViewportMetrics metrics();



    void scrollViewport();

}


