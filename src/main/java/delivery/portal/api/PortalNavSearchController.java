package delivery.portal.api;

import delivery.portal.model.ProjectRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.roles.RolesService;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/portal")
public class PortalNavSearchController {

    private static final List<NavEntry> WORKSPACE_PAGES = List.of(
            new NavEntry("/dashboard", "Overview", "overview dashboard"),
            new NavEntry("/projects", "Projects", "projects workspace"),
            new NavEntry("/generate", "Write test cases", "write test cases generate stories ollama"),
            new NavEntry("/record", "Record a test", "record recording chrome click through steps capture"),
            new NavEntry("/execute", "Run tests", "run tests execute prove"),
            new NavEntry("/automate", "Build code", "build code automate automation conversion upload"),
            new NavEntry("/runs", "Run history", "run history runs jobs evidence results"),
            new NavEntry("/packages", "Code packages", "code packages automation frameworks download"),
            new NavEntry("/bug-hunter", "Bug Hunter", "hunt bug hunter"),
            new NavEntry("/pricing", "Pricing", "pricing prices credits cost services"),
            new NavEntry("/guide", "Guide", "guide help authoring test case tc-guide how to how to write how to write test cases"),
            new NavEntry("/account", "Account", "account settings profile password"),
            new NavEntry("/admin/pricing", "Prices", "admin pricing edit prices costs"),
            new NavEntry("/admin/users", "Administration", "admin users invites"),
            new NavEntry("/admin/domains", "Domains", "admin domains allowlist")
    );

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final RolesService roles;

    public PortalNavSearchController(PortalStore store, CurrentUserService currentUser, RolesService roles) {
        this.store = store;
        this.currentUser = currentUser;
        this.roles = roles;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam("q") String q) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> hits = new ArrayList<>();
        PortalUser user = currentUser.requireUser();
        boolean admin = user.getRole() == PortalUser.Role.ADMIN;
        boolean head = roles.isHeadAdmin(user);
        for (NavEntry entry : WORKSPACE_PAGES) {
            if (isHeadOnlyPage(entry.url()) && !head) {
                continue;
            }
            if (isAdminOnlyPage(entry.url()) && !admin) {
                continue;
            }
            if (entry.matches(query)) {
                hits.add(hit(entry.url(), entry.label(), "page"));
            }
        }
        Long uid = user.getId();
        for (ProjectRecord project : store.listProjects(uid, true)) {
            String name = project.getName() == null ? "" : project.getName();
            String id = project.getProjectId();
            if (name.toLowerCase(Locale.ROOT).contains(query)
                    || id.toLowerCase(Locale.ROOT).contains(query)) {
                hits.add(hit("/projects/" + id, name.isBlank() ? id : name, "project"));
            }
        }
        return hits.size() > 12 ? hits.subList(0, 12) : hits;
    }

    private static boolean isHeadOnlyPage(String url) {
        return "/admin/pricing".equals(url) || "/admin/users".equals(url);
    }

    private static boolean isAdminOnlyPage(String url) {
        return "/admin/domains".equals(url);
    }

    private static Map<String, Object> hit(String url, String label, String kind) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("url", url);
        row.put("label", label);
        row.put("kind", kind);
        return row;
    }

    private record NavEntry(String url, String label, String keywords) {
        boolean matches(String q) {
            return label.toLowerCase(Locale.ROOT).contains(q)
                    || keywords.toLowerCase(Locale.ROOT).contains(q)
                    || url.toLowerCase(Locale.ROOT).contains(q.replace(' ', '-'));
        }
    }
}
