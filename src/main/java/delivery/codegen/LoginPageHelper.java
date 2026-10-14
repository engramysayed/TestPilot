package delivery.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Detects a login(user, pass) convenience API on login-shaped pages and collapses
 * type/type/click chron calls into a single {@code login(...)} invocation.
 */
public final class LoginPageHelper {
    private LoginPageHelper() {
    }

    public static boolean isLoginStem(String stem) {
        if (stem == null || stem.isBlank()) {
            return false;
        }
        String s = stem.toLowerCase(Locale.ROOT);
        return s.contains("login") || s.contains("signin") || s.contains("signon");
    }

    /**
     * @return map with userMethod, passMethod, submitMethod when the page can expose {@code login(String,String)}; else null
     */
    public static Map<String, String> detect(PageAccumulator.PageModel page) {
        if (page == null || !isLoginStem(page.stem())) {
            return null;
        }
        String user = null;
        String pass = null;
        String submit = null;
        for (PageAccumulator.MethodModel m : page.methods()) {
            if (m.needsValue() && "type".equalsIgnoreCase(m.action())) {
                String n = m.name().toLowerCase(Locale.ROOT);
                if (pass == null && (n.contains("pass") || n.contains("pwd") || n.contains("secret") || m.isSecret())) {
                    pass = m.name();
                } else if (user == null) {
                    user = m.name();
                } else if (pass == null) {
                    pass = m.name();
                }
            } else if (!m.needsValue() && "click".equalsIgnoreCase(m.action())) {
                String n = m.name().toLowerCase(Locale.ROOT);
                if (submit == null && (n.contains("submit") || n.contains("login") || n.contains("sign") || n.contains("continue"))) {
                    submit = m.name();
                } else if (submit == null) {
                    submit = m.name();
                }
            }
        }
        if (user != null && pass != null && submit != null) {
            return Map.of("userMethod", user, "passMethod", pass, "submitMethod", submit);
        }
        return null;
    }

    /**
     * When login chron is type + type + click on one login page, return a single synthetic call map for {@code login}.
     */
    public static List<Map<String, Object>> collapseLoginCalls(
            List<Map<String, Object>> loginChron, PageAccumulator pages) {
        if (loginChron == null || loginChron.size() < 3 || pages == null) {
            return null;
        }
        // Find first page class that has a login helper among the calls
        String pageClass = null;
        String pageVar = null;
        List<Map<String, Object>> typed = new ArrayList<>();
        Map<String, Object> click = null;
        for (Map<String, Object> c : loginChron) {
            if (!"action".equals(c.get("kind"))) {
                return null;
            }
            String method = String.valueOf(c.get("method"));
            boolean needsValue = Boolean.TRUE.equals(c.get("needsValue"));
            if (needsValue) {
                typed.add(c);
            } else if (method.toLowerCase(Locale.ROOT).startsWith("click")) {
                click = c;
            } else {
                return null;
            }
            if (pageClass == null) {
                pageClass = String.valueOf(c.get("pageClass"));
                pageVar = String.valueOf(c.get("pageVar"));
            } else if (!pageClass.equals(String.valueOf(c.get("pageClass")))) {
                return null;
            }
        }
        if (typed.size() != 2 || click == null) {
            return null;
        }
        PageAccumulator.PageModel page = null;
        for (PageAccumulator.PageModel pm : pages.pages().values()) {
            if (pageClass.equals(pm.pageClassName())) {
                page = pm;
                break;
            }
        }
        if (detect(page) == null) {
            return null;
        }
        Map<String, Object> userCall = typed.get(0);
        Map<String, Object> passCall = typed.get(1);
        // Prefer secret-marked as password
        if (Boolean.TRUE.equals(typed.get(0).get("secret")) && !Boolean.TRUE.equals(typed.get(1).get("secret"))) {
            userCall = typed.get(1);
            passCall = typed.get(0);
        }
        Map<String, Object> login = new java.util.LinkedHashMap<>();
        login.put("pageClass", pageClass);
        login.put("pageVar", pageVar);
        login.put("kind", "action");
        login.put("method", "login");
        login.put("needsValue", false);
        login.put("loginHelper", true);
        login.put("userPropKey", userCall.get("propKey"));
        login.put("userSecret", userCall.get("secret"));
        login.put("userValue", userCall.get("value"));
        login.put("passPropKey", passCall.get("propKey"));
        login.put("passSecret", passCall.get("secret"));
        login.put("passValue", passCall.get("value"));
        return List.of(login);
    }
}
