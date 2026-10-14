package delivery.mobile.ir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileStep(
        String stepId,
        String screenKey,
        MobileOperation operation,
        MobileContext context,
        MobileTarget target,
        MobileInput input,
        Integer timeoutMs,
        String retryPolicy,
        Boolean retryIfNoChange,
        MobileStepEvidence evidence,
        // ASSERT / CAPTURE / COMPARE / SCROLL extras (optional maps kept as strings for v1 simplicity)
        String assertType,
        String captureVar,
        String compareMode,
        String scrollDirection,
        Integer maxSwipes,
        Integer visibilityPercentage
) {
    public MobileStep {
        if (stepId == null || stepId.isBlank()) {
            throw new MobileIrException("STEP_ID", "stepId is required");
        }
        if (operation == null) {
            throw new MobileIrException("OP_REQUIRED", "operation is required");
        }
        if (retryIfNoChange == null) {
            retryIfNoChange = false;
        }
        if (Boolean.TRUE.equals(retryIfNoChange)) {
            if (operation != MobileOperation.TAP && operation != MobileOperation.LONG_PRESS) {
                throw new MobileIrException("RETRY_IF_NO_CHANGE",
                        "retryIfNoChange only allowed on TAP/LONG_PRESS");
            }
            if (operation == MobileOperation.TYPE || (input != null && "SECRET_REF".equals(input.kind()))) {
                throw new MobileIrException("RETRY_IF_NO_CHANGE",
                        "retryIfNoChange forbidden on TYPE/SECRET steps");
            }
        }
        validateOp(operation, context, target, input, assertType, captureVar, compareMode, scrollDirection);
        if (evidence == null) {
            evidence = new MobileStepEvidence(null, java.util.List.of());
        }
        if (visibilityPercentage == null && operation == MobileOperation.SCROLL_UNTIL) {
            visibilityPercentage = 50;
        }
    }

    private static void validateOp(
            MobileOperation operation,
            MobileContext context,
            MobileTarget target,
            MobileInput input,
            String assertType,
            String captureVar,
            String compareMode,
            String scrollDirection) {
        switch (operation) {
            case TYPE -> {
                if (input == null) {
                    throw new MobileIrException("TYPE_INPUT", "TYPE requires input");
                }
                if (target == null) {
                    throw new MobileIrException("TYPE_TARGET", "TYPE requires target");
                }
                if (context == null) {
                    throw new MobileIrException("TYPE_CONTEXT", "TYPE requires context");
                }
            }
            case TAP, CLEAR, LONG_PRESS -> {
                if (target == null) {
                    throw new MobileIrException(operation.name() + "_TARGET", operation + " requires target");
                }
                if (context == null) {
                    throw new MobileIrException(operation.name() + "_CONTEXT", operation + " requires context");
                }
            }
            case ASSERT -> {
                if (assertType == null || assertType.isBlank()) {
                    throw new MobileIrException("ASSERT_TYPE", "ASSERT requires assertType");
                }
                if (target == null) {
                    throw new MobileIrException("ASSERT_TARGET", "ASSERT requires target");
                }
            }
            case CAPTURE_TEXT -> {
                if (captureVar == null || captureVar.isBlank()) {
                    throw new MobileIrException("CAPTURE_VAR", "CAPTURE_TEXT requires captureVar");
                }
                if (target == null) {
                    throw new MobileIrException("CAPTURE_TARGET", "CAPTURE_TEXT requires target");
                }
            }
            case COMPARE_CAPTURED -> {
                if (captureVar == null || captureVar.isBlank()) {
                    throw new MobileIrException("COMPARE_VAR", "COMPARE_CAPTURED requires captureVar");
                }
                if (target == null) {
                    throw new MobileIrException("COMPARE_TARGET", "COMPARE_CAPTURED requires target");
                }
                if (compareMode == null || compareMode.isBlank()) {
                    throw new MobileIrException("COMPARE_MODE", "COMPARE_CAPTURED requires compareMode");
                }
            }
            case SCROLL_UNTIL -> {
                if (target == null) {
                    throw new MobileIrException("SCROLL_TARGET", "SCROLL_UNTIL requires target");
                }
                if (scrollDirection == null || scrollDirection.isBlank()) {
                    throw new MobileIrException("SCROLL_DIR", "SCROLL_UNTIL requires scrollDirection");
                }
            }
            case SWITCH_CONTEXT -> {
                if (context == null) {
                    throw new MobileIrException("SWITCH_CONTEXT", "SWITCH_CONTEXT requires context");
                }
            }
            case PRESS_BACK, HIDE_KEYBOARD, WAIT, SWIPE -> {
                // validated lightly in v1
            }
        }
    }
}
