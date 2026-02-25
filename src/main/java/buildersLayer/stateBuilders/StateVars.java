package buildersLayer.stateBuilders;

import utils.PropertyReader;

public class StateVars {
    private  static String lastScreenshotRef="step_0.png";
    private static String stateJson;
    private static final String scenario=PropertyReader.getProperty("SCENARIO");
    private static final int HTML_MAX_CHARS=Integer.parseInt(PropertyReader.getProperty("HTML_MAX_CHARS"));
    private  static final StringBuilder runningSummary = new StringBuilder();

    //getters
    public static String getLastScreenshotRef(){return lastScreenshotRef;}
    public static String getStateJson(){return stateJson;}
    public static String getScenario(){return scenario;}
    public static int getHTML_MAX_CHARS(){return HTML_MAX_CHARS;}
    public static StringBuilder getRunningSummary(){return runningSummary;}


    //setters
    public static void setLastScreenshotRef(String lastScreenshot){lastScreenshotRef=lastScreenshot;}
    public static void setRunningSummary(StringBuilder Summary){runningSummary.append(Summary);}
    public static void setStateJson(String state){stateJson=state;}

}
