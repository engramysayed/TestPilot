package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    private final CurrentUserService currentUser;

    public DashboardController(DashboardService dashboard, CurrentUserService currentUser) {
        this.dashboard = dashboard;
        this.currentUser = currentUser;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return dashboard.statsFor(currentUser.requireUserId());
    }

    @GetMapping("/attention")
    public List<Map<String, Object>> attention() {
        return dashboard.attentionFor(currentUser.requireUserId());
    }
}
