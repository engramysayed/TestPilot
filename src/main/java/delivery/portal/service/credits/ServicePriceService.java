package delivery.portal.service.credits;

import delivery.portal.persistence.credits.ServicePrice;
import delivery.portal.persistence.credits.ServicePriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Prices the account owner sets. Every job reads these, not a number typed by the browser. */
@Service
public class ServicePriceService {
    public static final long MAX_COST = 1_000_000L;

    private final ServicePriceRepository prices;
    private final DeliveryCreditsProperties defaults;

    public ServicePriceService(ServicePriceRepository prices, DeliveryCreditsProperties defaults) {
        this.prices = prices;
        this.defaults = defaults;
    }

    @Transactional
    public List<Map<String, Object>> list() {
        ensureSeeded();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServicePrice row : prices.findAllByOrderBySortOrderAsc()) {
            rows.add(toMap(row));
        }
        return rows;
    }

    @Transactional
    public long cost(CreditsBillingKind kind) {
        if (kind == null) {
            return 0L;
        }
        ensureSeeded();
        return prices.findById(kind.name()).map(ServicePrice::getCost).orElse(fallback(kind));
    }

    @Transactional
    public void save(Map<String, String> form) {
        ensureSeeded();
        for (ServicePrice row : prices.findAllByOrderBySortOrderAsc()) {
            String label = form.get("label_" + row.getCode());
            String costRaw = form.get("cost_" + row.getCode());
            if (label == null || costRaw == null) {
                throw new IllegalArgumentException("Enter a name and a price for every service.");
            }
            String cleaned = label.trim().replaceAll("[\\r\\n]+", " ");
            if (cleaned.isBlank() || cleaned.length() > 80) {
                throw new IllegalArgumentException("Each service name needs 1 to 80 characters.");
            }
            long cost;
            try {
                cost = Long.parseLong(costRaw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Prices are whole numbers.");
            }
            if (cost < 0 || cost > MAX_COST) {
                throw new IllegalArgumentException("Each price must be from 0 to " + MAX_COST + ".");
            }
            row.setLabel(cleaned);
            row.setCost(cost);
            row.setUpdatedAt(Instant.now());
            prices.save(row);
        }
    }

    private void ensureSeeded() {
        if (prices.count() == 0) {
            saveDefault("GENERATE", "Generate", defaults.getCost().getGenerate(), 1);
            saveDefault("EXECUTE", "Execute", defaults.getCost().getExecute(), 2);
            saveDefault("AUTOMATE", "Automation", defaults.getCost().getAutomate(), 3);
            saveDefault("HUNT", "Bug Hunter", defaults.getCost().getHunt(), 4);
            return;
        }
        renameLegacy("GENERATE", "Write test cases", "Generate");
        renameLegacy("EXECUTE", "Run a test", "Execute");
        renameLegacy("AUTOMATE", "Build the framework", "Automation");
    }

    private void renameLegacy(String code, String previousName, String featureName) {
        prices.findById(code).ifPresent(row -> {
            if (previousName.equals(row.getLabel())) {
                row.setLabel(featureName);
                row.setUpdatedAt(Instant.now());
                prices.save(row);
            }
        });
    }

    private void saveDefault(String code, String label, long cost, int order) {
        ServicePrice row = new ServicePrice();
        row.setCode(code);
        row.setLabel(label);
        row.setCost(Math.max(0, cost));
        row.setSortOrder(order);
        row.setUpdatedAt(Instant.now());
        prices.save(row);
    }

    private long fallback(CreditsBillingKind kind) {
        return switch (kind) {
            case GENERATE -> defaults.getCost().getGenerate();
            case EXECUTE -> defaults.getCost().getExecute();
            case AUTOMATE -> defaults.getCost().getAutomate();
            case HUNT -> defaults.getCost().getHunt();
        };
    }

    private static Map<String, Object> toMap(ServicePrice row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", row.getCode());
        out.put("label", row.getLabel());
        out.put("cost", row.getCost());
        out.put("unit", unitLabel(row.getCode()));
        out.put("detail", detail(row.getCode()));
        return out;
    }

    static String unitLabel(String code) {
        return switch (code == null ? "" : code.toUpperCase(Locale.ROOT)) {
            case "EXECUTE", "AUTOMATE" -> "Each test case";
            case "HUNT" -> "Each allowed action";
            default -> "Each writing job";
        };
    }

    static String detail(String code) {
        return switch (code == null ? "" : code.toUpperCase(Locale.ROOT)) {
            case "GENERATE" -> "One price for the whole writing job.";
            case "EXECUTE" -> "Charged once for each test case in the run, including Run it once.";
            case "AUTOMATE" -> "Charged once for each test case in the build, not once for the package.";
            case "HUNT" -> "Charged once for each allowed action: iterations × cycles × actions per cycle.";
            default -> "";
        };
    }
}
