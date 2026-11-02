package delivery.portal.service.credits;

import delivery.portal.persistence.credits.ServicePrice;
import delivery.portal.persistence.credits.ServicePriceRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServicePriceServiceTest {

    private static final Set<String> CORE = Set.of("GENERATE", "EXECUTE", "AUTOMATE", "HUNT");
    private static final Set<String> ADDONS = Set.of("VISUAL_DIFF", "SUITES_MONITOR", "HEAL_REVIEW");
    private static final Set<String> ALL = Set.of(
            "GENERATE", "EXECUTE", "AUTOMATE", "HUNT", "VISUAL_DIFF", "SUITES_MONITOR", "HEAL_REVIEW");

    @Test
    public void detailAndUnitLabelCoverCoreAndDraftAddons() {
        for (String code : ALL) {
            String detail = ServicePriceService.detail(code);
            Assert.assertFalse(detail.isBlank(), code + " detail blank");
            Assert.assertTrue(detail.length() <= 240, code + " detail too long: " + detail.length());
            String unit = ServicePriceService.unitLabel(code);
            Assert.assertFalse(unit.isBlank(), code + " unit blank");
        }
        Assert.assertTrue(ServicePriceService.detail("GENERATE").toLowerCase().contains("knowledge"));
        Assert.assertEquals(ServicePriceService.unitLabel("VISUAL_DIFF"), "Each review");
        Assert.assertEquals(ServicePriceService.unitLabel("SUITES_MONITOR"), "Each scheduled run");
        Assert.assertEquals(ServicePriceService.unitLabel("HEAL_REVIEW"), "Each proposal");
    }

    @Test
    public void ensureSeededListReturnsCoreFourPlusThreeDraftAddons() {
        MapStore store = new MapStore();
        ServicePriceRepository repo = mock(ServicePriceRepository.class);
        when(repo.count()).thenAnswer(inv -> (long) store.map.size());
        when(repo.findAllByOrderBySortOrderAsc()).thenAnswer(inv -> store.ordered());
        when(repo.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.map.get(inv.getArgument(0))));
        when(repo.save(any(ServicePrice.class))).thenAnswer(inv -> {
            ServicePrice row = inv.getArgument(0);
            store.map.put(row.getCode(), row);
            return row;
        });

        ServicePriceService service = new ServicePriceService(repo, new DeliveryCreditsProperties());
        List<Map<String, Object>> rows = service.list();

        Assert.assertEquals(rows.size(), 7);
        Set<String> codes = rows.stream().map(r -> String.valueOf(r.get("code"))).collect(Collectors.toSet());
        Assert.assertEquals(codes, ALL);
        Assert.assertTrue(codes.containsAll(CORE));
        Assert.assertTrue(codes.containsAll(ADDONS));

        Map<String, Long> costs = rows.stream()
                .collect(Collectors.toMap(r -> String.valueOf(r.get("code")), r -> ((Number) r.get("cost")).longValue()));
        Assert.assertEquals(costs.get("VISUAL_DIFF"), Long.valueOf(5L));
        Assert.assertEquals(costs.get("SUITES_MONITOR"), Long.valueOf(10L));
        Assert.assertEquals(costs.get("HEAL_REVIEW"), Long.valueOf(5L));

        for (Map<String, Object> row : rows) {
            String detail = String.valueOf(row.get("detail"));
            Assert.assertFalse(detail.isBlank());
            Assert.assertTrue(detail.length() <= 240, row.get("code") + " detail too long");
        }

        // Re-run must not reset owner-customized covers or wipe add-ons.
        store.map.get("VISUAL_DIFF").setCost(99L);
        store.map.get("GENERATE").setCovers("Owner custom covers for generate.");
        List<Map<String, Object>> again = service.list();
        Assert.assertEquals(again.size(), 7);
        Map<String, Object> visual = again.stream().filter(r -> "VISUAL_DIFF".equals(r.get("code"))).findFirst().orElseThrow();
        Assert.assertEquals(((Number) visual.get("cost")).longValue(), 99L);
        Map<String, Object> gen = again.stream().filter(r -> "GENERATE".equals(r.get("code"))).findFirst().orElseThrow();
        Assert.assertEquals(gen.get("detail"), "Owner custom covers for generate.");
    }

    @Test
    public void refreshLegacyCoversOnlyWhenStillCanned() {
        MapStore store = new MapStore();
        ServicePrice canned = row("GENERATE", "Generate", "Each writing job",
                "One price for the whole writing job.", 10L, 1);
        ServicePrice custom = row("EXECUTE", "Execute", "Each test case",
                "Owner wrote this cover.", 5L, 2);
        store.map.put(canned.getCode(), canned);
        store.map.put(custom.getCode(), custom);
        // Pre-seed other cores so count != 0 path runs refresh + addons.
        store.map.put("AUTOMATE", row("AUTOMATE", "Automation", "Each test case",
                "Charged once for each test case in the build, not once for the package.", 25L, 3));
        store.map.put("HUNT", row("HUNT", "Bug Hunter", "Each step",
                "A step is one click or one thing typed. The hunt page shows how many steps this run may try.", 15L, 4));

        ServicePriceRepository repo = mock(ServicePriceRepository.class);
        when(repo.count()).thenAnswer(inv -> (long) store.map.size());
        when(repo.findAllByOrderBySortOrderAsc()).thenAnswer(inv -> store.ordered());
        when(repo.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.map.get(inv.getArgument(0))));
        when(repo.save(any(ServicePrice.class))).thenAnswer(inv -> {
            ServicePrice r = inv.getArgument(0);
            store.map.put(r.getCode(), r);
            return r;
        });

        ServicePriceService service = new ServicePriceService(repo, new DeliveryCreditsProperties());
        List<Map<String, Object>> rows = service.list();
        Map<String, String> details = rows.stream()
                .collect(Collectors.toMap(r -> String.valueOf(r.get("code")), r -> String.valueOf(r.get("detail"))));

        Assert.assertNotEquals(details.get("GENERATE"), "One price for the whole writing job.");
        Assert.assertTrue(details.get("GENERATE").toLowerCase().contains("knowledge"));
        Assert.assertEquals(details.get("EXECUTE"), "Owner wrote this cover.");
        Assert.assertTrue(details.get("AUTOMATE").toLowerCase().contains("locator")
                || details.get("AUTOMATE").toLowerCase().contains("heal"));
        Assert.assertTrue(details.containsKey("VISUAL_DIFF"));
        Assert.assertTrue(details.containsKey("SUITES_MONITOR"));
        Assert.assertTrue(details.containsKey("HEAL_REVIEW"));
    }

    private static ServicePrice row(String code, String label, String chargedAs, String covers, long cost, int order) {
        ServicePrice r = new ServicePrice();
        r.setCode(code);
        r.setLabel(label);
        r.setChargedAs(chargedAs);
        r.setCovers(covers);
        r.setCost(cost);
        r.setSortOrder(order);
        return r;
    }

    private static final class MapStore {
        final Map<String, ServicePrice> map = new LinkedHashMap<>();

        List<ServicePrice> ordered() {
            List<ServicePrice> list = new ArrayList<>(map.values());
            list.sort(Comparator.comparingInt(ServicePrice::getSortOrder));
            return list;
        }
    }
}
