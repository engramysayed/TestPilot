package delivery.portal.model;

public class ProjectRecord {
    private final String projectId;
    private final String name;
    private final Long ownerUserId;
    private int latestVersion;
    /** ISO or display string for last conversion / job activity; may be blank. */
    private String lastModified = "";
    private String lastModifiedLabel = "";
    private String baseUrl = "";
    private boolean archived = false;
    private String archivedAt = "";

    public ProjectRecord(String projectId, String name, Long ownerUserId, int latestVersion) {
        this.projectId = projectId;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.latestVersion = latestVersion;
    }

    public String getProjectId() { return projectId; }
    public String getName() { return name; }
    public Long getOwnerUserId() { return ownerUserId; }
    public int getLatestVersion() { return latestVersion; }
    public void setLatestVersion(int version) { this.latestVersion = version; }
    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) {
        this.lastModified = lastModified == null ? "" : lastModified;
    }
    public String getLastModifiedLabel() { return lastModifiedLabel; }
    public void setLastModifiedLabel(String lastModifiedLabel) {
        this.lastModifiedLabel = lastModifiedLabel == null ? "" : lastModifiedLabel;
    }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public String getArchivedAt() { return archivedAt; }
    public void setArchivedAt(String archivedAt) {
        this.archivedAt = archivedAt == null ? "" : archivedAt;
    }
}
