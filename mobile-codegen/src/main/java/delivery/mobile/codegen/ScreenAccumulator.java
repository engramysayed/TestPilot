package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobileStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Accumulates one {@link ScreenModel} per screenKey from AE {@link MobileCaseIr} steps. */
public final class ScreenAccumulator {

    private final Map<String, ScreenModel> screens = new LinkedHashMap<>();
    private final NamingMap namingMap;

    public ScreenAccumulator(NamingMap namingMap) {
        this.namingMap = namingMap == null ? NamingMap.empty() : namingMap;
    }

    public void addAll(List<MobileCaseIr> cases) {
        if (cases == null) {
            return;
        }
        for (MobileCaseIr c : cases) {
            if (c == null || c.steps() == null) {
                continue;
            }
            for (MobileStep step : c.steps()) {
                add(step);
            }
        }
    }

    public void add(MobileStep step) {
        if (step == null) {
            return;
        }
        String key = step.screenKey();
        if (key == null) {
            key = "";
        }
        ScreenModel model = screens.computeIfAbsent(key, k -> new ScreenModel(k));
        model.ingest(step, namingMap);
    }

    public Map<String, ScreenModel> screens() {
        return screens;
    }

    public static final class ScreenModel {
        private final String screenKey;
        private final String stem;
        private final String screenClassName;
        private final Map<String, LocatorModel> locatorsByIdentity = new LinkedHashMap<>();
        private final List<ActionModel> actions = new ArrayList<>();
        private final List<AssertModel> asserts = new ArrayList<>();
        private final List<CaptureModel> captures = new ArrayList<>();
        private final Set<String> methodNames = new LinkedHashSet<>();

        ScreenModel(String screenKey) {
            this.screenKey = screenKey;
            this.stem = MobileCodegenNaming.screenStem(screenKey);
            this.screenClassName = MobileCodegenNaming.screenClassName(screenKey);
        }

        void ingest(MobileStep step, NamingMap names) {
            if (step.target() != null) {
                String id = MobileCodegenNaming.identityKey(screenKey, step);
                String field = names.pin(id, MobileCodegenNaming.locatorFieldName(step, null));
                locatorsByIdentity.putIfAbsent(id, new LocatorModel(field, MobileCodegenNaming.byExpression(step.target())));
            }
            MobileOperation op = step.operation();
            if (op == MobileOperation.ASSERT) {
                String id = "assert|" + MobileCodegenNaming.identityKey(screenKey, step) + "|" + step.assertType();
                String method = names.pin(id, MobileCodegenNaming.assertMethodName(step, null));
                method = dedupe(method);
                String locatorField = resolveLocatorField(step, names);
                boolean hasArg = step.assertType() != null
                        && (step.assertType().toLowerCase().contains("text")
                        || "exactText".equalsIgnoreCase(step.assertType()));
                String util = hasArg ? "assertExactText" : "assertVisible";
                asserts.add(new AssertModel(method, util, locatorField, hasArg, "String", "expected"));
                return;
            }
            if (op == MobileOperation.CAPTURE_TEXT) {
                String id = "capture|" + MobileCodegenNaming.identityKey(screenKey, step) + "|" + step.captureVar();
                String method = names.pin(id, "capture_" + MobileCodegenNaming.sanitizeJavaIdentifier(
                        step.captureVar(), "Value"));
                method = dedupe(method);
                String locatorField = resolveLocatorField(step, names);
                captures.add(new CaptureModel(method, "captureText", locatorField, step.captureVar(), false, null));
                return;
            }
            if (op == MobileOperation.COMPARE_CAPTURED) {
                String id = "compare|" + MobileCodegenNaming.identityKey(screenKey, step) + "|" + step.captureVar()
                        + "|" + step.compareMode();
                String method = names.pin(id, "compare_" + MobileCodegenNaming.sanitizeJavaIdentifier(
                        step.captureVar(), "Value"));
                method = dedupe(method);
                String locatorField = resolveLocatorField(step, names);
                String mode = step.compareMode() == null ? "EQUALS" : step.compareMode().trim().toUpperCase();
                captures.add(new CaptureModel(method, "compareCaptured", locatorField, step.captureVar(), true, mode));
                return;
            }
            if (op == MobileOperation.TAP || op == MobileOperation.TYPE || op == MobileOperation.CLEAR
                    || op == MobileOperation.LONG_PRESS || op == MobileOperation.SCROLL_UNTIL
                    || op == MobileOperation.SWIPE || op == MobileOperation.PRESS_BACK
                    || op == MobileOperation.HIDE_KEYBOARD) {
                String id = "action|" + MobileCodegenNaming.identityKey(screenKey, step) + "|" + op.name();
                String method = names.pin(id, MobileCodegenNaming.actionMethodName(step, null));
                method = dedupe(method);
                String locatorField = resolveLocatorField(step, names);
                boolean secret = op == MobileOperation.TYPE
                        && step.input() != null
                        && MobileCodeSecrets.isSecretKind(step.input().kind());
                boolean hasArg = op == MobileOperation.TYPE;
                String util;
                String settle;
                boolean passSettleKind;
                if (secret) {
                    util = "typeSecret";
                    settle = "TYPE_SECRET";
                    passSettleKind = false;
                } else {
                    util = switch (op) {
                        case TAP -> "tap";
                        case TYPE -> "type";
                        case CLEAR -> "clear";
                        case LONG_PRESS -> "longPress";
                        default -> "tap";
                    };
                    settle = op.name();
                    passSettleKind = op == MobileOperation.TAP || op == MobileOperation.TYPE;
                }
                actions.add(new ActionModel(
                        method, util, locatorField, hasArg, "String",
                        secret ? "secretRefKey" : "value", settle, passSettleKind, secret));
            }
        }

        private String resolveLocatorField(MobileStep step, NamingMap names) {
            if (step.target() == null) {
                return "element_El_Locator";
            }
            String id = MobileCodegenNaming.identityKey(screenKey, step);
            LocatorModel loc = locatorsByIdentity.get(id);
            if (loc != null) {
                return loc.fieldName();
            }
            return names.pin(id, MobileCodegenNaming.locatorFieldName(step, null));
        }

        private String dedupe(String method) {
            String m = method;
            int i = 2;
            while (!methodNames.add(m)) {
                m = method + "_" + i++;
            }
            return m;
        }

        public String screenKey() { return screenKey; }
        public String stem() { return stem; }
        public String screenClassName() { return screenClassName; }
        public List<LocatorModel> locators() { return List.copyOf(locatorsByIdentity.values()); }
        public List<ActionModel> actions() { return List.copyOf(actions); }
        public List<AssertModel> asserts() { return List.copyOf(asserts); }
        public List<CaptureModel> captures() { return List.copyOf(captures); }
    }

    public record LocatorModel(String fieldName, String byExpression) {}

    public record ActionModel(
            String methodName, String utilMethod, String locatorField,
            boolean hasArg, String argType, String argName, String settleKind,
            boolean passSettleKind, boolean secretRef) {
        public ActionModel(
                String methodName, String utilMethod, String locatorField,
                boolean hasArg, String argType, String argName, String settleKind) {
            this(methodName, utilMethod, locatorField, hasArg, argType, argName, settleKind, true, false);
        }
    }

    public record AssertModel(
            String methodName, String utilMethod, String locatorField,
            boolean hasArg, String argType, String argName) {}

    /** CAPTURE_TEXT / COMPARE_CAPTURED screen helpers (prove semantics). */
    public record CaptureModel(
            String methodName, String utilMethod, String locatorField,
            String captureVar, boolean compare, String compareMode) {}
}
