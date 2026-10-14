package delivery.mobile.runner.bind;

/** Matching normalization (arch §5.2). Never applied to stored selector bytes. */
public final class MatchNormalizer {
    private MatchNormalizer() {}

    public static String normalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    public static boolean equalsNormalized(String a, String b) {
        return normalize(a).equals(normalize(b));
    }
}
