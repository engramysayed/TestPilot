package delivery.portal.service.credits;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side cost table only — never trust client-supplied cost. */
@Component
public class CreditCostTable {
    private final DeliveryCreditsProperties props;

    public CreditCostTable(DeliveryCreditsProperties props) {
        this.props = props;
    }

    public long cost(CreditsBillingKind kind) {
        if (kind == null) {
            return 0L;
        }
        return switch (kind) {
            case GENERATE -> props.getCost().getGenerate();
            case EXECUTE -> props.getCost().getExecute();
            case AUTOMATE -> props.getCost().getAutomate();
            case HUNT -> props.getCost().getHunt();
        };
    }

    public Map<String, Object> asCostsDto() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generate", props.getCost().getGenerate());
        out.put("execute", props.getCost().getExecute());
        out.put("automate", props.getCost().getAutomate());
        out.put("hunt", props.getCost().getHunt());
        out.put("enabled", props.isEnabled());
        return out;
    }

    public boolean enabled() {
        return props.isEnabled();
    }
}
