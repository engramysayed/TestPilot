package project.utils;

public class TimeManager {

    public static String getTimeStamp(){
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
                .replace(":", "-").replace(" ", "_");
    }

    public static String getSimpleTimeStamp(){
        return Long.toString(System.currentTimeMillis());
    }
}
