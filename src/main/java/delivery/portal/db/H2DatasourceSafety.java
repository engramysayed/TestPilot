package delivery.portal.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Issue #2 (H2 hardening). Pure checks on the portal datasource configuration.
 *
 * <ul>
 *   <li>{@code AUTO_SERVER=TRUE} makes H2 open a TCP server on a random port (published in
 *       {@code portal-db.lock.db}) that any local process can reach. It is never allowed in production
 *       and is reported as a warning in development.</li>
 *   <li>An empty datasource password is fatal in production and a warning in development. The password
 *       comes from {@code KEEL_DB_PASSWORD} (environment or config), never from the repo.</li>
 * </ul>
 */
public final class H2DatasourceSafety {

    public record Result(List<String> errors, List<String> warnings) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private H2DatasourceSafety() {
    }

    public static boolean hasAutoServer(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toUpperCase(Locale.ROOT).replace(" ", "");
        return u.contains(";AUTO_SERVER=TRUE");
    }

    public static boolean isH2(String url) {
        return url != null && url.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:h2:");
    }

    public static Result check(boolean production, String url, String password) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!isH2(url)) {
            return new Result(List.of(), List.of());
        }
        if (hasAutoServer(url)) {
            String msg = "spring.datasource.url must not use AUTO_SERVER=TRUE "
                    + "(it opens an H2 TCP port reachable by every local process)";
            (production ? errors : warnings).add(msg);
        }
        boolean blank = password == null || password.isBlank();
        boolean inMemory = url.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:h2:mem:");
        if (blank && !inMemory) {
            String msg = "datasource password is empty; set KEEL_DB_PASSWORD (env or external config, never the repo)";
            (production ? errors : warnings).add(msg);
        } else if (blank && production) {
            errors.add("datasource password is empty; set KEEL_DB_PASSWORD");
        }
        return new Result(List.copyOf(errors), List.copyOf(warnings));
    }
}
