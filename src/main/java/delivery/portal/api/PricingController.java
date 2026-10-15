package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.credits.ServicePriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Signed-in users can read prices. They cannot change them here. */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {
    private final ServicePriceService prices;
    private final CurrentUserService currentUser;

    public PricingController(ServicePriceService prices, CurrentUserService currentUser) {
        this.prices = prices;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Map<String, Object> list() {
        currentUser.requireUser();
        return Map.of("prices", prices.list());
    }
}
