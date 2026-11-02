package delivery.portal.service.credits;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side cost table only — never trust client-supplied cost. */
@Component
public class CreditCostTable {
    private final DeliveryCreditsProperties props;
    private final ServicePriceService prices;

    public CreditCostTable(DeliveryCreditsProperties props) {
        this(props, null);
    }

    @Autowired
    public CreditCostTable(DeliveryCreditsProperties props, ServicePriceService prices) {
        this.props = props;
        this.prices = prices;
    }

    public long cost(CreditsBillingKind kind) {
        if (kind == null) {
            return 0L;
        }
        if (prices != null) {
            return prices.cost(kind);
        }
        return switch (kind) {
            case GENERATE -> props.getCost().getGenerate();
            case EXECUTE -> props.getCost().getExecute();
            case AUTOMATE -> props.getCost().getAutomate();
            case HUNT -> props.getCost().getHunt();
        };
    }

    /** Admin ServicePrice by code (add-ons and core). Falls back to 0 when no price service. */
    public long costByCode(String code) {
        if (code == null || code.isBlank()) {
            return 0L;
        }
        if (prices != null) {
            return prices.costByCode(code);
        }
        return 0L;
    }

    public Map<String, Object> asCostsDto() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generate", cost(CreditsBillingKind.GENERATE));
        out.put("execute", cost(CreditsBillingKind.EXECUTE));
        out.put("automate", cost(CreditsBillingKind.AUTOMATE));
        out.put("hunt", cost(CreditsBillingKind.HUNT));
        out.put("enabled", props.isEnabled());
        return out;
    }

    public boolean enabled() {
        return props.isEnabled();
    }
}
