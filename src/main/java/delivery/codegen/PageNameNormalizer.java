package delivery.codegen;

public final class PageNameNormalizer {
    private PageNameNormalizer() {}

    public static boolean isAlias(String pageName) {
        if (pageName == null || pageName.isBlank()) return true;
        String n = pageName.trim();
        if ("Page".equals(n)) return true;
        return "Login".equalsIgnoreCase(n)
                || "LoginForm".equalsIgnoreCase(n)
                || "TargetLogin".equals(n);
    }

    public static String canonical(String pageName, String urlStem) {
        String stem = urlStem == null ? "" : urlStem.trim();
        if (!isAlias(pageName)) {
            return pageName.trim();
        }
        if ("LoginPage".equalsIgnoreCase(stem) || stem.toLowerCase().contains("login")) {
            return "LoginPage";
        }
        if (stem.isBlank() || "Page".equals(stem) || "Home".equals(stem)) {
            return "LoginPage";
        }
        return stem;
    }
}
