package delivery.codegen;

import delivery.ir.SecretStep;
import delivery.settle.SettlePolicy;

/**
 * Codegen view of the engine's IR {@code secret} flag (r2 SPEC section 2; {@link SecretStep} is authoritative).
 * String-keyed helpers for the template's settle kinds and redacted messages.
 */
public final class CodegenSecrets {
    /** Engine mask {@link SecretStep#MASK}. */
    public static final String MASK = SecretStep.MASK;

    private CodegenSecrets() {
    }

    /** A8 settle key for a generated action: secret type/clear -> typeSecret (via {@link SecretStep#settleKind}). */
    public static String settleKind(String action, boolean secret) {
        String a = action == null ? "" : action.trim();
        try {
            return SecretStep.settleKind(SettlePolicy.Action.of(a), secret).key;
        } catch (IllegalArgumentException notAnA8Kind) {
            return a;
        }
    }

    /** Value as it may appear in a codegen message. */
    public static String displayValue(ProvenStep step) {
        return SecretStep.logValue(step.value(), step.secret());
    }
}
