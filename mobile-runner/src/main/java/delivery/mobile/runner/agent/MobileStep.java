package delivery.mobile.runner.agent;

import delivery.mobile.ir.MobileLineIntent;

import java.util.List;

/**
 * One interpreted test-case line for a native Android run (English or Arabic).
 * Classification lives in {@link MobileLineIntent} so Automate emit uses the same steps.
 */
public record MobileStep(Kind kind, String target, String value, String direction, String raw) {

    public enum Kind { TAP, TYPE, CLEAR, ASSERT_VISIBLE, ASSERT_NOT_VISIBLE, BACK, SWIPE, LAUNCH, SKIP }

    public static MobileStep parse(String line, String testDataLine) {
        MobileLineIntent intent = MobileLineIntent.parse(line, testDataLine);
        return new MobileStep(map(intent.kind()), intent.target(), intent.value(), intent.direction(), intent.raw());
    }

    public static List<String> quoted(String raw) {
        return MobileLineIntent.quoted(raw);
    }

    public static String normalize(String s) {
        return MobileLineIntent.normalize(s);
    }

    private static Kind map(MobileLineIntent.Kind kind) {
        return switch (kind) {
            case TAP -> Kind.TAP;
            case TYPE -> Kind.TYPE;
            case CLEAR -> Kind.CLEAR;
            case ASSERT_VISIBLE -> Kind.ASSERT_VISIBLE;
            case ASSERT_NOT_VISIBLE -> Kind.ASSERT_NOT_VISIBLE;
            case BACK -> Kind.BACK;
            case SWIPE -> Kind.SWIPE;
            case LAUNCH -> Kind.LAUNCH;
            case SKIP -> Kind.SKIP;
        };
    }
}
