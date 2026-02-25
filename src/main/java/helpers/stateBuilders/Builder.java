package helpers.stateBuilders;
import executionLayer.actionExecute;
import parsingLayer.HtmlSlimmer;
import parsingLayer.JsonMapper;
import static helpers.stateBuilders.StateVars.*;

public class Builder {

    public static void startState(actionExecute executor){

        setStateJson(JsonMapper.buildPlannerStart(
                getScenario(),
                executor.getUrl(),
                HtmlSlimmer.slim(executor.getHtml(), getHTML_MAX_CHARS()),
                getLastScreenshotRef(),
                getRunningSummary().toString()
        ));
    }

    public static void updateState(actionExecute executor){
        setStateJson(JsonMapper.buildPlannerStart(
                getScenario(),
                executor.getUrl(),
                HtmlSlimmer.slim(executor.getHtml(), getHTML_MAX_CHARS()),
                getLastScreenshotRef(),
                getRunningSummary().toString()
        ));
    }

}
