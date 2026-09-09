package delivery.codegen;

import delivery.ir.TcDraft;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase-2: assign POM page names from URL path / landmarks so locators cluster
 * across TCs instead of collapsing onto a single {@code Page} class.
 */
public final class PageClusterer {
    private PageClusterer() {
    }

    public static String pageNameFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "Page";
        }
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return pageNameFromHost(uri.getHost());
            }
            List<String> segments = meaningfulPathSegments(path);
            if (isLoginPath(path, segments)) {
                return "LoginPage";
            }
            if (segments.size() >= 2) {
                String leafSeg = segments.get(segments.size() - 1).toLowerCase(Locale.ROOT);
                String prevSeg = segments.get(segments.size() - 2);
                if (Set.of("new", "create", "edit").contains(leafSeg)) {
                    String entity = singularizeEntity(prevSeg);
                    return capitalize(leafSeg) + entity;
                }
            }
            String leaf = path;
            int slash = leaf.lastIndexOf('/');
            if (slash >= 0) {
                leaf = leaf.substring(slash + 1);
            }
            if (leaf.isBlank()) {
                String[] parts = path.split("/");
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isBlank()) {
                        leaf = parts[i];
                        break;
                    }
                }
            }
            // Root-ish path still empty after trim → use host brand (never "Www")
            if (leaf.isBlank()) {
                return pageNameFromHost(uri.getHost());
            }
            int dot = leaf.lastIndexOf('.');
            if (dot > 0) {
                leaf = leaf.substring(0, dot);
            }
            return toPageClassStem(leaf);
        } catch (IllegalArgumentException e) {
            return "Page";
        }
    }

    /**
     * Brand stem from host: skip generic labels ({@code www}, {@code m}, …) so
     * {@code www.example.com/} → {@code Example}, not {@code Www}.
     */
    static String pageNameFromHost(String host) {
        if (host == null || host.isBlank()) {
            return "Home";
        }
        String[] labels = host.toLowerCase(Locale.ROOT).split("\\.");
        int i = 0;
        while (i < labels.length && isGenericHostLabel(labels[i])) {
            i++;
        }
        String leaf;
        if (i < labels.length && !isPublicSuffixOnly(labels, i)) {
            leaf = labels[i];
        } else if (labels.length >= 2) {
            leaf = labels[labels.length - 2];
        } else {
            leaf = labels[0];
        }
        if (leaf == null || leaf.isBlank() || isGenericHostLabel(leaf)) {
            return "Home";
        }
        return toPageClassStem(leaf);
    }

    private static boolean isGenericHostLabel(String label) {
        if (label == null || label.isBlank()) {
            return true;
        }
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "www", "www2", "www3", "m", "mobile", "app", "api", "cdn", "static" -> true;
            default -> false;
        };
    }

    /** Avoid treating {@code com}/{@code co} alone as the brand when only TLD remains. */
    private static boolean isPublicSuffixOnly(String[] labels, int index) {
        if (index >= labels.length - 1) {
            return true;
        }
        String last = labels[labels.length - 1];
        return last.length() <= 3 && index == labels.length - 1;
    }

    static List<String> meaningfulPathSegments(String path) {
        List<String> out = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return out;
        }
        for (String part : path.split("/")) {
            if (part.isBlank()) {
                continue;
            }
            String seg = part;
            int dot = seg.lastIndexOf('.');
            if (dot > 0) {
                seg = seg.substring(0, dot);
            }
            if (seg.isBlank()) {
                continue;
            }
            out.add(seg);
        }
        return out;
    }

    static String singularizeEntity(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Entity";
        }
        String[] parts = raw.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            sb.append(singularizeWord(part));
        }
        return sb.isEmpty() ? "Entity" : sb.toString();
    }

    static boolean isLoginPath(String path, List<String> segments) {
        if (path != null) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.contains("/login") || lower.contains("-login")) {
                return true;
            }
        }
        for (String seg : segments) {
            if (seg == null || seg.isBlank()) {
                continue;
            }
            String s = seg.toLowerCase(Locale.ROOT);
            if ("login".equals(s) || s.endsWith("login")) {
                return true;
            }
        }
        return false;
    }

    private static String singularizeWord(String part) {
        String p = part.toLowerCase(Locale.ROOT);
        if (p.endsWith("ies") && p.length() > 3) {
            p = p.substring(0, p.length() - 3) + "y";
        } else if (p.endsWith("s") && p.length() > 1 && !p.endsWith("ss")) {
            p = p.substring(0, p.length() - 1);
        }
        return Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    private static String capitalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static String toPageClassStem(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Page";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (cleaned.isBlank()) {
            return "Page";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        String name = sb.toString();
        if (name.isEmpty()) {
            return "Page";
        }
        if (Character.isDigit(name.charAt(0))) {
            name = "Page" + name;
        }
        return name;
    }

    /** Prefer per-step page names already stamped at prove time; fill blanks from fallback URL. */
    public static List<ProvenStep> reclusterSteps(List<ProvenStep> steps, String fallbackUrl) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        String defaultPage = pageNameFromUrl(fallbackUrl);
        List<ProvenStep> out = new ArrayList<>();
        for (ProvenStep s : steps) {
            String page = s.pageName();
            if (PageNameNormalizer.isAlias(page)) {
                page = inferFromRationale(s.rationale(), defaultPage);
            }
            page = PageNameNormalizer.canonical(page, defaultPage);
            out.add(s.withPageName(page));
        }
        return out;
    }

    public static TcDraft reclusterDraft(TcDraft draft) {
        String url = draft.lastPageUrl();
        String loginStemUrl = draft.loginFormUrl() != null && !draft.loginFormUrl().isBlank()
                ? draft.loginFormUrl()
                : url;
        List<ProvenStep> proven = reclusterSteps(draft.provenSteps(), url);
        List<ProvenStep> login = reclusterSteps(draft.loginSteps(), loginStemUrl);
        return new TcDraft(
                draft.tcId(), draft.title(), draft.stepsText(), draft.expectedResult(),
                draft.status(), proven, login, draft.needsLoginBeforeMethod(),
                draft.blockerStepIndex(), draft.blockerIntent(), draft.failureReason(),
                draft.evidenceDir(), draft.retryCountOnBlocker(), draft.lastPageUrl(),
                draft.healTier(), draft.healSkipReason(), draft.loginFormUrl());
    }

    private static String inferFromRationale(String rationale, String defaultPage) {
        // Do not invent a frozen "Login" page — URL stem is the source of truth
        return defaultPage == null || defaultPage.isBlank() ? "Page" : defaultPage;
    }
}
