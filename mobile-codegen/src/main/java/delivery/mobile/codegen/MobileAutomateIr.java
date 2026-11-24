package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileContext;
import delivery.mobile.ir.MobileInput;
import delivery.mobile.ir.MobileLineIntent;
import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobilePlatform;
import delivery.mobile.ir.MobileProof;
import delivery.mobile.ir.MobileStep;
import delivery.mobile.ir.MobileTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns workbook lines into mobile IR.
 * When {@code walked} is empty, every control is a text shell of the Excel label.
 * When a phone walk supplied locators, those replace the shells in step order.
 */
public final class MobileAutomateIr {

    /** One locator from a phone walk, or a shell standing in for a missed bind. */
    public record Locator(String strategy, String value, boolean shell) {
        public Locator {
            strategy = strategy == null || strategy.isBlank() ? "text" : strategy;
            value = value == null ? "" : value;
        }

        public static Locator shell(String label) {
            String v = label == null ? "" : label.trim();
            return new Locator("text", v.isEmpty() ? "unknown" : v, true);
        }
    }

    private MobileAutomateIr() {}

    public static MobileCaseIr build(
            String tcId,
            String appPackage,
            List<String> steps,
            List<String> testData,
            List<String> expected,
            List<Locator> walked) {
        String pkg = appPackage == null ? "" : appPackage.trim();
        List<Locator> locs = walked == null ? List.of() : walked;
        int cursor = 0;
        List<MobileStep> out = new ArrayList<>();
        List<String> lines = steps == null ? List.of() : steps;
        List<String> data = testData == null ? List.of() : testData;
        for (int i = 0; i < lines.size(); i++) {
            String datum = i < data.size() ? data.get(i) : "";
            MobileLineIntent intent = MobileLineIntent.parse(lines.get(i), datum);
            if (intent.kind() == MobileLineIntent.Kind.SKIP || intent.kind() == MobileLineIntent.Kind.LAUNCH) {
                continue;
            }
            Locator loc = null;
            if (intent.takesLocator()) {
                loc = cursor < locs.size() ? locs.get(cursor) : Locator.shell(intent.target());
                cursor++;
            }
            MobileStep step = toStep("s" + (out.size() + 1), pkg, intent, loc);
            if (step != null) {
                out.add(step);
            }
        }
        if (expected != null) {
            for (String exp : expected) {
                List<String> q = MobileLineIntent.quoted(exp);
                if (q.isEmpty()) {
                    continue;
                }
                MobileLineIntent intent = new MobileLineIntent(
                        MobileLineIntent.Kind.ASSERT_VISIBLE, q.get(0), "", "", exp == null ? "" : exp);
                Locator loc = cursor < locs.size() ? locs.get(cursor) : Locator.shell(q.get(0));
                cursor++;
                MobileStep step = toStep("s" + (out.size() + 1), pkg, intent, loc);
                if (step != null) {
                    out.add(step);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(toStep("s1", pkg,
                    new MobileLineIntent(MobileLineIntent.Kind.ASSERT_VISIBLE, "screen", "", "", "screen"),
                    Locator.shell("screen")));
        }
        boolean anyReal = out.stream().anyMatch(s -> s.target() != null && "HIERARCHY".equals(s.target().origin()));
        return new MobileCaseIr(
                MobileCaseIr.SCHEMA_V1,
                MobilePlatform.ANDROID,
                tcId == null || tcId.isBlank() ? "TC" : tcId,
                "automate",
                "automate",
                "automate",
                "automate",
                new MobileProof(anyReal ? "WALKED" : "SHELL", anyReal ? "DEVICE" : "WORKBOOK", "automate"),
                List.of(),
                out);
    }

    private static MobileStep toStep(String stepId, String pkg, MobileLineIntent intent, Locator loc) {
        MobileContext ctx = new MobileContext("NATIVE", pkg, null, null);
        return switch (intent.kind()) {
            case BACK -> new MobileStep(stepId, "main", MobileOperation.PRESS_BACK, ctx, null, null,
                    null, null, false, null, null, null, null, null, null, null);
            case SWIPE -> new MobileStep(stepId, "main", MobileOperation.SWIPE, ctx,
                    target(Locator.shell("screen")), null,
                    null, null, false, null, null, null, null,
                    intent.direction() == null || intent.direction().isBlank() ? "DOWN" : intent.direction(),
                    null, null);
            case TAP, CLEAR -> new MobileStep(stepId, "main",
                    intent.kind() == MobileLineIntent.Kind.CLEAR ? MobileOperation.CLEAR : MobileOperation.TAP,
                    ctx, target(loc == null ? Locator.shell(intent.target()) : loc), null,
                    null, null, false, null, null, null, null, null, null, null);
            case TYPE -> new MobileStep(stepId, "main", MobileOperation.TYPE, ctx,
                    target(loc == null ? Locator.shell(intent.target()) : loc),
                    input(intent),
                    null, null, false, null, null, null, null, null, null, null);
            case ASSERT_VISIBLE, ASSERT_NOT_VISIBLE -> new MobileStep(stepId, "main", MobileOperation.ASSERT, ctx,
                    target(loc == null ? Locator.shell(intent.target()) : loc), null,
                    null, null, false, null, "visible", null, null, null, null, null);
            default -> null;
        };
    }

    private static MobileTarget target(Locator loc) {
        Locator use = loc == null ? Locator.shell("unknown") : loc;
        String origin = use.shell() ? "USER" : "HIERARCHY";
        return new MobileTarget(use.strategy(), use.value(), origin, 1);
    }

    private static MobileInput input(MobileLineIntent intent) {
        String blob = (intent.target() + " " + intent.raw()).toLowerCase(Locale.ROOT);
        if (blob.contains("password") || blob.contains("otp") || blob.contains("secret") || blob.contains("pin")) {
            return new MobileInput("SECRET_REF", "password", null, true);
        }
        return new MobileInput("LITERAL", null, intent.value() == null ? "" : intent.value(), false);
    }
}
