package delivery.portal.web;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.PortalUser;
import delivery.portal.security.CurrentUserService;
import delivery.portal.security.JobSecretCrypto;
import delivery.portal.persistence.JobEntity;
import delivery.portal.service.DashboardService;
import delivery.portal.service.InviteService;
import delivery.portal.service.JobLinkEnricher;
import delivery.portal.service.PortalStore;
import delivery.portal.service.WorkspacePackageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    public PortalUiController(DashboardService dashboard, CurrentUserService currentUser,
                              PortalStore store, InviteService invites,
                              DeliveryPortalProperties portalProperties,
                              JobLinkEnricher jobLinks, WorkspacePackageService workspacePackages) {
        this.dashboard = dashboard;
        this.currentUser = currentUser;
        this.store = store;
        this.invites = invites;
        this.portalProperties = portalProperties;
        this.jobLinks = jobLinks;
        this.workspacePackages = workspacePackages;
    }

    private void addNav(Model model) {
        PortalUser user = currentUser.requireUser();
        model.addAttribute("email", user.getEmail());
        model.addAttribute("isAdmin", user.getRole() == PortalUser.Role.ADMIN);
        model.addAttribute("navActive", "");
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
        addNav(model);
        model.addAttribute("navActive", "dashboard");
        model.addAttribute("stats", dashboard.statsFor(currentUser.requireUserId()));
        return "dashboard";
    }

    @GetMapping("/projects")
    public String projects(Model model) {
        addNav(model);
        model.addAttribute("navActive", "projects");
        model.addAttribute("projects", store.listProjects(currentUser.requireUserId()));
        return "projects";
    }

    @GetMapping("/projects/{projectId}")
    public String projectDetail(@PathVariable("projectId") String projectId,
                                @RequestParam(value = "tab", defaultValue = "summary") String tab,
                                Model model) {
        addNav(model);
        model.addAttribute("navActive", "projects");
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
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("lastJobUsername", lastJobUsernameForProject(projectId, ownerUserId));
        model.addAttribute("precisionAuthoringEnabled", portalProperties.isPrecisionAuthoringEnabled());
        model.addAttribute("defaultPrecisionMaxCalls", portalProperties.getPrecisionMaxCallsPerJob());
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

    @GetMapping("/automate")
    public String automate(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model);
        model.addAttribute("navActive", "automate");
        model.addAttribute("projects", store.listProjects(currentUser.requireUserId()));
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        model.addAttribute("finalReviseEnabled", portalProperties.isFinalReviseEnabled());
        return "upload";
    }

    @GetMapping("/generate")
    public String generate(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model);
        model.addAttribute("navActive", "generate");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        return "generate";
    }

    @GetMapping("/execute")
    public String execute(@RequestParam(value = "projectId", required = false) String projectId, Model model) {
        addNav(model);
        model.addAttribute("navActive", "execute");
        model.addAttribute("projectId", projectId == null ? "" : projectId);
        return "execute";
    }

    @GetMapping("/packages")
    public String packages(Model model) {
        Long ownerUserId = currentUser.requireUserId();
        addNav(model);
        model.addAttribute("navActive", "packages");
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
        addNav(model);
        model.addAttribute("navActive", "runs");
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
        addNav(model);
        model.addAttribute("navActive", "bug-hunter");
        return "bug-hunter";
    }

    @GetMapping("/upload")
    public String upload(@RequestParam(value = "projectId", required = false) String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return "redirect:/automate";
        }
        return "redirect:/automate?projectId=" + projectId;
    }

    @GetMapping("/tc-guide")
    public String tcGuide(Model model) {
        addNav(model);
        model.addAttribute("navActive", "tc-guide");
        return "tc-guide";
    }

    @GetMapping({"/runs", "/jobs"})
    public String runs(Model model) {
        addNav(model);
        model.addAttribute("navActive", "runs");
        model.addAttribute("jobs", enrichedJobsForOwner(currentUser.requireUserId()));
        return "jobs";
    }

    private List<Map<String, Object>> enrichedJobsForOwner(Long ownerUserId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JobEntity entity : store.listJobEntities(ownerUserId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            String jobKind = entity.getJobKind() == null ? JobRecord.JobKind.CONVERT.name() : entity.getJobKind();
            row.put("jobId", entity.getJobId());
            row.put("projectId", entity.getProjectId());
            row.put("jobKind", jobKind);
            row.put("mode", entity.getMode());
            row.put("status", entity.getStatus());
            row.put("progressCurrent", entity.getProgressCurrent());
            row.put("progressTotal", entity.getProgressTotal());
            row.put("message", entity.getMessage() == null ? "" : entity.getMessage());
            row.put("downloadable", JobRecord.isDownloadable(JobRecord.parseJobKind(jobKind), entity.getStatus()));
            jobLinks.enrich(row, entity.getJobId(), JobRecord.parseJobKind(jobKind), entity.getStatus());
            rows.add(row);
        }
        return rows;
    }

    @GetMapping("/status")
    public String status(@RequestParam("jobId") String jobId, Model model) {
        addNav(model);
        model.addAttribute("navActive", "runs");
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

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        addNav(model);
        model.addAttribute("navActive", "users");
        return "admin-users";
    }

    @GetMapping("/admin/domains")
    public String adminDomains(Model model) {
        addNav(model);
        model.addAttribute("navActive", "domains");
        return "admin-domains";
    }

    @GetMapping("/account")
    public String account(Model model) {
        addNav(model);
        model.addAttribute("navActive", "account");
        return "account";
    }
}
