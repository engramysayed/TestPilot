package delivery.portal.service;

import delivery.excel.ManualTestCase;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Drops generated TC rows that fall outside the pasted story scope. */
public final class GeneratedTcScopeFilter {
    private GeneratedTcScopeFilter() {
    }

    public static List<ManualTestCase> apply(String stories, List<ManualTestCase> cases) {
        if (stories == null || stories.isBlank() || cases == null || cases.isEmpty()) {
            return cases;
        }
        if (!isNegativeOnlyLoginScope(stories)) {
            return cases;
        }
        List<ManualTestCase> kept = cases.stream()
                .filter(tc -> !isOutOfScopeHappyLogin(tc))
                .collect(Collectors.toList());
        return kept.isEmpty() ? cases : kept;
    }

    static boolean isNegativeOnlyLoginScope(String stories) {
        String s = stories.toLowerCase(Locale.ROOT);
        boolean negative = s.contains("invalid")
                || s.contains("wrong password")
                || s.contains("incorrect password")
                || s.contains("login fail")
                || s.contains("login error")
                || s.contains("invalid login")
                || s.contains("negative");
        boolean successRequested = s.contains("successful login")
                || s.contains("successfully log")
                || s.contains("happy path")
                || s.contains("valid login only")
                || s.contains("positive login")
                || (s.contains("news feed") && !s.contains("not open") && !s.contains("do not")
                && !s.contains("must not") && !s.contains("don't"));
        return negative && !successRequested;
    }

    static boolean isOutOfScopeHappyLogin(ManualTestCase tc) {
        if (tc == null) {
            return false;
        }
        String title = lower(tc.title());
        String tags = lower(tc.tags());
        String steps = lower(tc.steps());
        String expected = lower(tc.expectedResult());
        if (title.contains("valid credential") || title.contains("successful login")) {
            return true;
        }
        if (tags.contains("happy") || (tags.contains("smoke") && title.contains("valid"))) {
            return true;
        }
        if (title.contains("valid") && steps.contains("valid password")
                && !steps.contains("wrong") && !steps.contains("invalid")) {
            return true;
        }
        if (expected.contains("news feed") && !expected.contains("not redirect")) {
            return false;
        }
        return title.matches(".*\\bvalid\\b.*\\b(login|credential|password)\\b.*")
                && !title.contains("invalid")
                && !steps.contains("wrong");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
