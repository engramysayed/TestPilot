package project.utils.reports;

import java.util.concurrent.Callable;

/** Thin Allure step wrapper; falls back to direct invoke when Allure is absent. */
public final class AllureSteps {
    private AllureSteps() {}

    public static <T> T step(String name, Callable<T> body) {
        try {
            return io.qameta.allure.Allure.step(name, () -> {
                try {
                    return body.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (NoClassDefFoundError e) {
            try {
                return body.call();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static void step(String name, Runnable body) {
        step(name, () -> {
            body.run();
            return null;
        });
    }

    /**
     * Attach an Allure parameter. Secret-looking keys or explicit secretRef never get raw values —
     * only {@link Secrets#refLabel(String)}.
     */
    public static void parameter(String name, String value) {
        parameter(name, value, false);
    }

    public static void parameter(String name, String value, boolean secretRef) {
        String safeName = name == null ? "param" : name;
        String safeValue;
        if (secretRef || Secrets.looksLikeSecretKey(safeName)) {
            safeValue = Secrets.refLabel(value == null || value.isBlank() ? safeName : value);
        } else {
            safeValue = value == null ? "" : value;
        }
        try {
            io.qameta.allure.Allure.parameter(safeName, safeValue);
        } catch (NoClassDefFoundError ignored) {
            // Allure absent — no-op
        }
    }
}
