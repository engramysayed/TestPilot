package delivery.portal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "delivery")
public class DeliveryPortalProperties {
    private boolean dryRun = true;
    private String storeRoot = "./delivery-store";
    private String workDir = "./delivery-work";
    private String templateRoot = "./customer-framework-template";
    private String llmBaseUrl = "http://127.0.0.1:11434";
    private String llmModel = "qwen2.5:latest";
    private String adminEmail = "admin@testpilot.local";
    private String adminPassword = "ChangeMeAdmin1!";
    private int inviteExpiryDays = 14;
    /** Public site URL used in invite emails (no trailing slash). */
    private String publicBaseUrl = "http://localhost:8080";
    private boolean mailEnabled = false;
    private String mailFrom = "noreply@testpilot.local";
    private boolean finalReviseEnabled = false;
    private String finalReviseBaseUrl = "https://agentrouter.org";
    private String finalReviseModel = "claude-opus-5";
    /** Optional; prefer env AGENTROUTER_API_KEY. */
    private String finalReviseApiKey = "";
    private boolean generateEnabled = true;
    private String generateModel = "gemma4:e2b";
    private List<String> generateModels = new ArrayList<>(List.of("gemma4:e2b", "qwen2.5:latest"));
    /** Per-story Ollama wait for generate jobs. Async JSON batches often exceed 2 minutes. */
    private int generateTimeoutSeconds = 600;
    private RetentionProperties retention = new RetentionProperties();

    public static class RetentionProperties {
        /** Age in days before execute-runs and work dirs are deleted; 0 disables sweeper. */
        private int days = 14;

        public int getDays() { return days; }
        public void setDays(int days) { this.days = days; }
    }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public String getStoreRoot() { return storeRoot; }
    public void setStoreRoot(String storeRoot) { this.storeRoot = storeRoot; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }
    public String getTemplateRoot() { return templateRoot; }
    public void setTemplateRoot(String templateRoot) { this.templateRoot = templateRoot; }
    public String getLlmBaseUrl() { return llmBaseUrl; }
    public void setLlmBaseUrl(String llmBaseUrl) { this.llmBaseUrl = llmBaseUrl; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public int getInviteExpiryDays() { return inviteExpiryDays; }
    public void setInviteExpiryDays(int inviteExpiryDays) { this.inviteExpiryDays = inviteExpiryDays; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public boolean isMailEnabled() { return mailEnabled; }
    public void setMailEnabled(boolean mailEnabled) { this.mailEnabled = mailEnabled; }
    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String mailFrom) { this.mailFrom = mailFrom; }
    public boolean isFinalReviseEnabled() { return finalReviseEnabled; }
    public void setFinalReviseEnabled(boolean finalReviseEnabled) { this.finalReviseEnabled = finalReviseEnabled; }
    public String getFinalReviseBaseUrl() { return finalReviseBaseUrl; }
    public void setFinalReviseBaseUrl(String finalReviseBaseUrl) { this.finalReviseBaseUrl = finalReviseBaseUrl; }
    public String getFinalReviseModel() { return finalReviseModel; }
    public void setFinalReviseModel(String finalReviseModel) { this.finalReviseModel = finalReviseModel; }
    public String getFinalReviseApiKey() { return finalReviseApiKey; }
    public void setFinalReviseApiKey(String finalReviseApiKey) { this.finalReviseApiKey = finalReviseApiKey; }
    public boolean isGenerateEnabled() { return generateEnabled; }
    public void setGenerateEnabled(boolean generateEnabled) { this.generateEnabled = generateEnabled; }
    public String getGenerateModel() { return generateModel; }
    public void setGenerateModel(String generateModel) { this.generateModel = generateModel; }
    public List<String> getGenerateModels() { return generateModels; }
    public void setGenerateModels(List<String> generateModels) { this.generateModels = generateModels; }
    public int getGenerateTimeoutSeconds() { return generateTimeoutSeconds; }
    public void setGenerateTimeoutSeconds(int generateTimeoutSeconds) {
        this.generateTimeoutSeconds = generateTimeoutSeconds;
    }
    public RetentionProperties getRetention() { return retention; }
    public void setRetention(RetentionProperties retention) { this.retention = retention; }
}
