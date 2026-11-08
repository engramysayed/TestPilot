package delivery.portal.web;

import delivery.mobile.MobileProperties;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.PortalUser;
import delivery.portal.security.CurrentUserService;
import delivery.portal.security.JobSecretCrypto;
import delivery.portal.service.DashboardService;
import delivery.portal.service.InviteService;
import delivery.portal.service.JobLinkEnricher;
import delivery.portal.service.PortalStore;
import delivery.portal.service.WorkspacePackageService;
import delivery.portal.service.credits.CreditService;
import delivery.portal.roles.ApprovalStatus;
import delivery.portal.roles.RolesService;
import delivery.portal.service.credits.ServicePriceService;
import delivery.portal.service.llm.LlmSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PortalUiController {
    private final DashboardService dashboard;
    private final CurrentUserService currentUser;
    private final PortalStore store;
    private final InviteService invites;
    private final DeliveryPortalProperties portalProperties;
    private final JobLinkEnricher jobLinks;
    private final WorkspacePackageService workspacePackages;
    private final MobileProperties mobileProperties;
    private final CreditService creditService;
    private final RolesService roles;
    private final ServicePriceService prices;
    private final LlmSettingsService llmSettings;

    public PortalUiController(DashboardService dashboard, CurrentUserService currentUser,
                              PortalStore store, InviteService invites,
                              DeliveryPortalProperties portalProperties,
                              JobLinkEnricher jobLinks, WorkspacePackageService workspacePackages,
                              MobileProperties mobileProperties, CreditService creditService,
                              RolesService roles, ServicePriceService prices,
                              LlmSettingsService llmSettings) {
        this.dashboard = dashboard;
        this.currentUser = currentUser;
        this.store = store;
        this.invites = invites;
        this.portalProperties = portalProperties;
        this.jobLinks = jobLinks;
        this.workspacePackages = workspacePackages;
        this.mobileProperties = mobileProperties;
        this.creditService = creditService;
        this.roles = roles;
        this.prices = prices;
        this.llmSettings = llmSettings;
    }

    private void addNav(Model model, String navActive) {
        PortalUser user = currentUser.requireUser();
        String email = user.getEmail();
        model.addAttribute("email", email);
        model.addAttribute("isAdmin", user.getRole() == PortalUser.Role.ADMIN);
        model.addAttribute("headAdmin", roles.isHeadAdmin(user));
        model.addAttribute("navActive", navActive);
        model.addAttribute("reviewCasesPerBatch", llmSettings.reviewCasesPerBatch());
        model.addAttribute("userInitials", initialsFromEmail(email));
        model.addAttribute("workspaceTitle", workspaceTitleFromEmail(email));
        model.addAttribute("workspaceSubtitle", user.getRole() == PortalUser.Role.ADMIN
                ? "Platform admin"
                : "Personal workspace · Owner");
        model.addAttribute("pageCrumb", "Workspace");
        model.addAttribute("pageTitle", pageTitleForNav(navActive));
        boolean creditsOn = portalProperties.isCreditsEnabled();
        model.addAttribute("creditsEnabled", creditsOn);
        model.addAttribute("sidebarIaEnabled", portalProperties.isSidebarIaEnabled());
        model.addAttribute("rolesUiEnabled", portalProperties.isRolesUiEnabled());
        ApprovalStatus st = user.getApprovalStatus() == null ? ApprovalStatus.PENDING_APPROVAL : user.getApprovalStatus();
        model.addAttribute("approvalStatus", st.name());
        model.addAttribute("accountApproved", st == ApprovalStatus.APPROVED);
        if (creditsOn) {
            Object bal = creditService.userCreditsDto(user.getId()).get("balance");
            model.addAttribute("creditsBalance", bal == null ? 0L : bal);
        }
    }

    private static String initialsFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "??";
        }
        String local = email.split("@")[0].trim();
        if (local.contains(".")) {
            String[] parts = local.split("\\.");
            StringBuilder sb = new StringBuilder(2);
            for (String part : parts) {
                if (!part.isEmpty()) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                }
                if (sb.length() >= 2) {
                    break;
                }
            }
            return sb.length() >= 2 ? sb.substring(0, 2) : local.substring(0, Math.min(2, local.length())).toUpperCase();
        }
        return local.length() >= 2
                ? local.substring(0, 2).toUpperCase()
                : local.toUpperCase();
    }

    private static String workspaceTitleFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "My workspace";
        }
        String local = email.split("@")[0].trim();
        if (local.contains(".")) {
            local = local.split("\\.")[0];
        }
        if (local.isEmpty()) {
            return "My workspace";
        }
        return Character.toUpperCase(local.charAt(0)) + local.substring(1) + "'s workspace";
    }

    private static String pageTitleForNav(String navActive) {
        if (navActive == null || navActive.isBlank()) {
            return "Keel";
        }
        return switch (navActive) {
            case "dashboard" -> "Overview";
            case "projects" -> "Projects";
            case "generate" -> "Write test cases";
            case "generate-from-knowledge" -> "Generate from knowledge";
            case "record" -> "Record a test";
            case "plain-cases" -> "Plain-English cases";
            case "suites" -> "Suites";
            case "execute" -> "Run tests";
            case "automate" -> "Build code";
            case "bug-hunter" -> "Bug Hunter";
            case "runs" -> "Run history";
            case "packages" -> "Code packages";
            case "guide" -> "Guide";
            case "tc-guide" -> "Guide";
            case "account" -> "Account";
            case "pricing" -> "Pricing";
            case "prices" -> "Prices";
            case "users" -> "Administration";
            case "domains" -> "Domains";
            case "roles" -> "Roles";
            case "llm-settings" -> "LLM & API keys";
            case "prompts" -> "Prompts";
            default -> "Keel";
        };
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        addNav(model, "dashboard");
        model.addAttribute("stats", dashboard.statsFor(currentUser.requireUserId()));
        return "dashboard";
    }

    @GetMapping("/projects")
    public String projects(Model model) {
        addNav(model, "projects");
        model.addAttribute("projects", store.listProjects(currentUser.requireUserId()));
        model.addAttribute("mobileEnabled", mobileProperties.isEnabled());
        return "projects";
    }

    @GetMapping("/projects/{projectId}")
    public String projectDetail(@PathVariable("projectId") String projectId,
                                @RequestParam(value = "tab", defaultValue = "summary") String tab,
                                Model model) {
        addNav(model, "projects");
        Long ownerUserId = currentUser.requireUserId();
        var owned = store.getOwnedProject(projectId, ownerUserId);
        if (owned.isEmpty()) {
            return "redirect:/projects";
        }
        String activeTab = switch (tab) {
            case "summary", "automation", "tcs", "settings" -> tab;
            default -> "summary";
        };
        model.addAttribute("project", owned.get());
        model.addAttribute("projectId", projectId);
        model.addAttribute("pageTitle", owned.get().getName());
        model.addAttribute("pageCrumb", "Projects");
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("lastJobUsername", lastJobUsernameForProject(projectId, ownerUserId));
        model.addAttribute("precisionAuthoringEnabled", portalProperties.isPrecisionAuthoringEnabled());
        model.addAttribute("defaultPrecisionMaxCalls", portalProperties.getPrecisionMaxCallsPerJob());
        model.addAttribute("mobileEnabled", mobileProperties.isEnabled());
        model.addAttribute("projectPlatform", owned.get().getPlatform() == null ? "WEB" : owned.get().getPlatform());
        model.addAttribute("mobileCanEdit", store.canOperate(projectId, ownerUserId));
        return "project-detail";
    }

    private String lastJobUsernameForProject(String projectId, Long ownerUserId) {
        return store.listJobEntities(ownerUserId).stream()
                .filter(j -> projectId.equals(j.getProjectId()))
                .findFirst()
                .map(PortalUiController::decryptJobUsername)
                .orElse("");
    }

    private static String decryptJobUsername(JobEntity job) {
        try {
            String u = JobSecretCrypto.decrypt(job.getUsernameCipher());
            return u == null ? "" : u;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Automate UI lives in {@code upload.html} (workbook upload + conversion). {@code automate.html} is not used. */
    @GetMapping("/automate")
    public String automate(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model, "automate");
        model.addAttribute("projects", store.listProjects(currentUser.requireUserId()));
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        model.addAttribute("finalReviseEnabled", portalProperties.isFinalReviseEnabled());
        return "upload";
    }

    @GetMapping("/generate")
    public String generate(@RequestParam(value = "projectId", required = false) String projectId,
                           @RequestParam(value = "record", required = false) String record,
                           Model model) {
        if ("1".equals(record)) {
            if (projectId == null || projectId.isBlank()) {
                return "redirect:/record";
            }
            return "redirect:/record?projectId=" + URLEncoder.encode(projectId, StandardCharsets.UTF_8);
        }
        addNav(model, "generate");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        model.addAttribute("generateStructuredIntake", portalProperties.isGenerateStructuredIntake());
        return "generate";
    }

    @GetMapping("/generate-from-knowledge")
    public String generateFromKnowledge(@RequestParam(value = "projectId", required = false) String projectId,
                                        Model model) {
        addNav(model, "generate-from-knowledge");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        return "generate-from-knowledge";
    }

    @GetMapping("/record")
    public String record(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model, "record");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        return "record";
    }


    @GetMapping("/plain-cases")
    public String plainCases(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model, "plain-cases");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        return "plain-cases";
    }

    @GetMapping("/structured-plans")
    public String structuredPlans(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model, "structured-plans");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        return "structured-plans";
    }

    @GetMapping("/run-memory")
    public String runMemory(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model, "run-memory");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        return "run-memory";
    }

    @GetMapping("/suites")
    public String suites(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model, "suites");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        return "suites";
    }

    @GetMapping("/execute")
    public String execute(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model, "execute");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        model.addAttribute("mobileEnabled", mobileProperties.isEnabled());
        model.addAttribute("retryVerdicts", delivery.portal.service.RetryMetricsView.legend());
        return "execute";
    }

    @GetMapping("/packages")
    public String packages(Model model) {
        Long ownerUserId = currentUser.requireUserId();
        addNav(model, "packages");
        model.addAttribute("packages", workspacePackages.listPackagesForOwner(ownerUserId));
        List<Map<String, Object>> projectRows = new ArrayList<>();
        for (var project : store.listProjects(ownerUserId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", project.getProjectId());
            row.put("name", project.getName());
            row.put("canOperate", store.canOperate(project.getProjectId(), ownerUserId));
            row.put("jobRunning", store.hasRunningJob(project.getProjectId()));
            projectRows.add(row);
        }
        model.addAttribute("projectRows", projectRows);
        return "packages";
    }

    @GetMapping("/evidence")
    public String evidence(@RequestParam("jobId") String jobId,
                           @RequestParam(value = "tcId", required = false) String tcId,
                           Model model) {
        addNav(model, "runs");
        model.addAttribute("pageTitle", "Evidence");
        model.addAttribute("pageCrumb", "Run history");
        model.addAttribute("jobId", jobId);
        model.addAttribute("tcId", tcId == null ? "" : tcId);
        var owned = store.getOwnedJob(jobId, currentUser.requireUserId());
        if (owned.isEmpty() || owned.get().getJobKind() != JobRecord.JobKind.EXECUTE) {
            return "redirect:/runs";
        }
        model.addAttribute("projectId", owned.get().getProjectId());
        return "evidence";
    }

    @GetMapping("/bug-hunter")
    public String bugHunter(Model model) {
        addNav(model, "bug-hunter");
        model.addAttribute("huntTriageEnabled", portalProperties.isHuntTriageEnabled());
        return "bug-hunter";
    }

    @GetMapping("/upload")
    public String upload(@RequestParam(value = "projectId", required = false) String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return "redirect:/automate";
        }
        return "redirect:/automate?projectId=" + projectId;
    }

    @GetMapping("/guide")
    public String guide(Model model) {
        addNav(model, "guide");
        return "guide";
    }

    /** Old TC guide URL — bookmarks land on the product Guide TC authoring section. */
    @GetMapping("/tc-guide")
    public String tcGuideRedirect() {
        return "redirect:/guide#tc-authoring";
    }

    @GetMapping({"/runs", "/jobs"})
    public String runs(Model model) {
        addNav(model, "runs");
        model.addAttribute("jobs", enrichedJobsForOwner(currentUser.requireUserId()));
        return "jobs";
    }

    private List<Map<String, Object>> enrichedJobsForOwner(Long ownerUserId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        // Cache project names so a big run list doesn't hit the DB per row.
        Map<String, String> nameCache = new java.util.HashMap<>();
        for (JobEntity entity : store.listJobEntities(ownerUserId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            String jobKind = entity.getJobKind() == null ? JobRecord.JobKind.CONVERT.name() : entity.getJobKind();
            String projectId = entity.getProjectId();
            row.put("jobId", entity.getJobId());
            row.put("projectId", projectId);
            row.put("projectName", nameCache.computeIfAbsent(projectId == null ? "" : projectId,
                    pid -> pid.isBlank() ? "" : store.getOwnedProject(pid, ownerUserId)
                            .map(p -> p.getName()).orElse(pid)));
            row.put("jobKind", jobKind);
            row.put("mode", entity.getMode());
            row.put("status", entity.getStatus());
            row.put("progressCurrent", entity.getProgressCurrent());
            row.put("progressTotal", entity.getProgressTotal());
            row.put("message", entity.getMessage() == null ? "" : entity.getMessage());
            // Human label for the Run column: 'Nov 09 · 16:42' from the completed/created timestamp.
            java.time.Instant when = entity.getCompletedAt() != null ? entity.getCompletedAt() : entity.getCreatedAt();
            row.put("startedAtLabel", when == null ? "" : RUN_WHEN.format(when));
            row.put("downloadable", JobRecord.isDownloadable(JobRecord.parseJobKind(jobKind), entity.getStatus()));
            jobLinks.enrich(row, entity.getJobId(), JobRecord.parseJobKind(jobKind), entity.getStatus());
            rows.add(row);
        }
        return rows;
    }

    private static final java.time.format.DateTimeFormatter RUN_WHEN =
            java.time.format.DateTimeFormatter.ofPattern("MMM dd · HH:mm")
                    .withZone(java.time.ZoneId.systemDefault());

    @GetMapping("/status")
    public String status(@RequestParam("jobId") String jobId, Model model) {
        addNav(model, "runs");
        model.addAttribute("pageTitle", "Status");
        model.addAttribute("pageCrumb", "Run history");
        model.addAttribute("jobId", jobId);
        String status = "QUEUED";
        String message = "Waiting for worker…";
        String jobKind = JobRecord.JobKind.CONVERT.name();
        int passed = 0;
        int todo = 0;
        int progressCurrent = 0;
        int progressTotal = 0;
        Instant createdAt = null;
        var owned = store.getOwnedJob(jobId, currentUser.requireUserId());
        if (owned.isPresent()) {
            var job = owned.get();
            jobKind = job.getJobKind().name();
            status = job.getStatus().name();
            createdAt = job.getCreatedAt();
            boolean downloadable = JobRecord.isDownloadable(job.getJobKind(), job.getStatus());
            message = job.getMessage() == null || job.getMessage().isBlank()
                    ? (downloadable ? "Completed" : status)
                    : job.getMessage();
            passed = job.getPassedCount();
            todo = job.getTodoCount();
            progressCurrent = job.getProgressCurrent();
            progressTotal = job.getProgressTotal();
            if (progressTotal <= 0 && downloadable) {
                progressTotal = Math.max(1, passed + todo);
                progressCurrent = progressTotal;
            }
        } else {
            status = "UNKNOWN";
            message = "Job not found for this account";
        }
        model.addAttribute("jobKind", jobKind);
        model.addAttribute("isExecuteJob", JobRecord.JobKind.EXECUTE.name().equals(jobKind));
        model.addAttribute("isGenerateBatchJob", JobRecord.JobKind.GENERATE_BATCH.name().equals(jobKind));
        model.addAttribute("isGenerateCompareJob", JobRecord.JobKind.GENERATE_COMPARE.name().equals(jobKind));
        model.addAttribute("isHuntJob", JobRecord.JobKind.HUNT.name().equals(jobKind));
        model.addAttribute("jobStatus", status);
        model.addAttribute("jobMessage", message);
        model.addAttribute("passedCount", passed);
        model.addAttribute("todoCount", todo);
        model.addAttribute("progressLabel", progressCurrent + " / " + progressTotal);
        boolean downloadable = JobRecord.isDownloadable(JobRecord.parseJobKind(jobKind), status);
        int pct = progressTotal > 0 ? Math.min(100, (progressCurrent * 100) / progressTotal)
                : (downloadable ? 100 : 0);
        model.addAttribute("progressPercent", pct);
        model.addAttribute("jobCreatedAt", createdAt == null ? "" : createdAt.toString());
        model.addAttribute("jobElapsedLabel", JobRecord.workingForLabel(createdAt, Instant.now()));
        return "status";
    }

    @GetMapping("/invite/{token}")
    public String inviteForm(@PathVariable("token") String token, Model model) {
        try {
            var invite = invites.requireValidInvite(token);
            model.addAttribute("token", token);
            model.addAttribute("email", invite.getEmail());
            model.addAttribute("error", null);
        } catch (IllegalArgumentException e) {
            model.addAttribute("token", token);
            model.addAttribute("email", "");
            model.addAttribute("error", e.getMessage());
        }
        return "invite";
    }

    @PostMapping("/invite/{token}")
    public String acceptInvite(@PathVariable("token") String token,
                               @RequestParam("password") String password,
                               Model model) {
        try {
            invites.acceptInvite(token, password);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException e) {
            model.addAttribute("token", token);
            model.addAttribute("email", "");
            model.addAttribute("error", e.getMessage());
            try {
                model.addAttribute("email", invites.requireValidInvite(token).getEmail());
            } catch (IllegalArgumentException ignored) {
                // keep empty email
            }
            return "invite";
        }
    }

    @GetMapping("/admin/invites")
    public String adminInvites() {
        return "redirect:/admin/users";
    }

    @GetMapping("/pricing")
    public String pricing(Model model) {
        addNav(model, "pricing");
        model.addAttribute("prices", prices.list());
        return "pricing";
    }

    @GetMapping("/admin/pricing")
    public String adminPricing(@RequestParam(value = "saved", required = false) String saved, Model model) {
        requireHeadPage();
        addNav(model, "prices");
        model.addAttribute("prices", prices.list());
        model.addAttribute("saved", saved != null);
        return "admin-pricing";
    }

    @PostMapping("/admin/pricing")
    public String savePricing(@RequestParam Map<String, String> form, Model model) {
        requireHeadPage();
        try {
            prices.save(form);
        } catch (IllegalArgumentException e) {
            addNav(model, "prices");
            model.addAttribute("prices", prices.list());
            model.addAttribute("error", e.getMessage());
            return "admin-pricing";
        }
        return "redirect:/admin/pricing?saved";
    }

    @GetMapping("/admin/refunds")
    public String adminRefunds(Model model) {
        requireHeadPage();
        addNav(model, "refunds");
        model.addAttribute("pageTitle", "Refunds");
        return "admin-refunds";
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        requireHeadPage();
        addNav(model, "users");
        return "admin-users";
    }

    @GetMapping("/admin/domains")
    public String adminDomains(Model model) {
        addNav(model, "domains");
        return "admin-domains";
    }

    @GetMapping("/admin/roles")
    public String adminRoles(Model model) {
        requireHeadPage();
        addNav(model, "roles");
        model.addAttribute("rolesUiEnabled", portalProperties.isRolesUiEnabled());
        if (!portalProperties.isRolesUiEnabled()) {
            return "redirect:/dashboard";
        }
        return "admin-roles";
    }

    @GetMapping("/admin/llm-settings")
    public String adminLlmSettings(Model model) {
        requireHeadPage();
        addNav(model, "llm-settings");
        Map<String, Object> view = llmSettings.maskedView();
        model.addAttribute("builtInVerbs", listAttr(view.get("builtInVerbs")));
        model.addAttribute("authoringVerbAliases", listAttr(view.get("authoringVerbAliases")));
        model.addAttribute("cachedOllamaModels", listAttr(view.get("cachedOllamaModels")));
        model.addAttribute("ollamaChatModel", view.get("ollamaChatModel"));
        model.addAttribute("ollamaVisionModel", view.get("ollamaVisionModel"));
        model.addAttribute("ollamaBaseUrl", view.get("ollamaBaseUrl"));
        Object codeOnly = view.get("codeOnlyVerbs");
        if (codeOnly instanceof List<?> list && !list.isEmpty()) {
            List<String> bits = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    bits.add(String.valueOf(item).trim());
                }
            }
            if (!bits.isEmpty()) {
                model.addAttribute("codeOnlyVerbsText", String.join("; ", bits));
            }
        }
        return "admin-llm-settings";
    }

    @GetMapping("/admin/prompts")
    public String adminPrompts(Model model) {
        requireHeadPage();
        addNav(model, "prompts");
        return "admin-prompts";
    }

    @GetMapping("/account")
    public String account(Model model) {
        addNav(model, "account");
        return "account";
    }

    private void requireHeadPage() {
        if (!roles.isHeadAdmin(currentUser.requireUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the account owner can open this page.");
        }
    }

    private static List<?> listAttr(Object raw) {
        return raw instanceof List<?> list ? list : List.of();
    }
}
