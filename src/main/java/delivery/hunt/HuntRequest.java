package delivery.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Durable start payload for a HUNT job (serialized to request.json). */
public final class HuntRequest {
    public static final int DEFAULT_SCENARIO_CAP = 5;
    public static final int DEFAULT_CYCLE_CEILING = 8;
    public static final int DEFAULT_ACTION_CAP = 5;
    public static final int MAX_SCENARIO_CAP = 20;
    public static final int MAX_CYCLE_CEILING = 30;
    public static final int MAX_ACTION_CAP = 15;

    private String projectId;
    private String jobId;
    private String baseUrl;
    private List<String> tcIds = new ArrayList<>();
    private String userStory = "";
    private String planner = "ollama"; // ollama | cursor
    private int scenarioCap = DEFAULT_SCENARIO_CAP;
    private int cycleCeiling = DEFAULT_CYCLE_CEILING;
    private int actionCapPerCycle = DEFAULT_ACTION_CAP;
    private String domMode = "auto";
    private boolean strategiesEnabled = true;

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public List<String> getTcIds() { return tcIds; }
    public void setTcIds(List<String> tcIds) {
        this.tcIds = tcIds == null ? new ArrayList<>() : new ArrayList<>(tcIds);
    }
    public String getUserStory() { return userStory == null ? "" : userStory; }
    public void setUserStory(String userStory) { this.userStory = userStory == null ? "" : userStory; }
    public String getPlanner() { return planner; }
    public void setPlanner(String planner) {
        this.planner = planner == null ? "ollama" : planner.trim().toLowerCase(Locale.ROOT);
    }
    public int getScenarioCap() { return scenarioCap; }
    public void setScenarioCap(int scenarioCap) { this.scenarioCap = scenarioCap; }
    public int getCycleCeiling() { return cycleCeiling; }
    public void setCycleCeiling(int cycleCeiling) { this.cycleCeiling = cycleCeiling; }
    public int getActionCapPerCycle() { return actionCapPerCycle; }
    public void setActionCapPerCycle(int actionCapPerCycle) { this.actionCapPerCycle = actionCapPerCycle; }
    public String getDomMode() { return domMode == null ? "auto" : domMode; }
    public void setDomMode(String domMode) { this.domMode = domMode; }
    public boolean isStrategiesEnabled() { return strategiesEnabled; }
    public void setStrategiesEnabled(boolean strategiesEnabled) {
        this.strategiesEnabled = strategiesEnabled;
    }

    public void normalize() {
        setPlanner(planner);
        if (!"cursor".equals(planner) && !"ollama".equals(planner)) {
            setPlanner("ollama");
        }
        if (scenarioCap < 1) {
            scenarioCap = DEFAULT_SCENARIO_CAP;
        }
        if (scenarioCap > MAX_SCENARIO_CAP) {
            scenarioCap = MAX_SCENARIO_CAP;
        }
        if (cycleCeiling < 1) {
            cycleCeiling = DEFAULT_CYCLE_CEILING;
        }
        if (cycleCeiling > MAX_CYCLE_CEILING) {
            cycleCeiling = MAX_CYCLE_CEILING;
        }
        if (actionCapPerCycle < 1) {
            actionCapPerCycle = DEFAULT_ACTION_CAP;
        }
        if (actionCapPerCycle > MAX_ACTION_CAP) {
            actionCapPerCycle = MAX_ACTION_CAP;
        }
        if (tcIds == null) {
            tcIds = new ArrayList<>();
        }
        List<String> cleaned = new ArrayList<>();
        for (String id : tcIds) {
            if (id != null && !id.isBlank()) {
                cleaned.add(id.trim());
            }
        }
        tcIds = cleaned;
        String dm = getDomMode().trim().toLowerCase(Locale.ROOT);
        if (!"auto".equals(dm) && !"map".equals(dm) && !"slim".equals(dm)) {
            domMode = "auto";
        } else {
            domMode = dm;
        }
    }
}
