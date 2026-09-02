package delivery.portal.service;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GeneratedTcScopeFilterTest {

    private static final String INVALID_ONLY_STORY = """
            As a Facebook user I want clear feedback when I try to sign in with invalid credentials.
            AC: wrong password shows error; do not open News Feed.
            """;

    @Test
    public void dropsHappyPathWhenScopeIsInvalidLoginOnly() {
        ManualTestCase happy = new ManualTestCase(
                "TC_03", "Valid Credentials Check", "", "steps", "feed visible", "P1", "happy", "", "", "AUTOMATE");
        ManualTestCase negative = new ManualTestCase(
                "TC_01", "Invalid password", "", "steps", "error shown", "P1", "negative", "", "", "AUTOMATE");
        List<ManualTestCase> out = GeneratedTcScopeFilter.apply(INVALID_ONLY_STORY, List.of(happy, negative));
        Assert.assertEquals(out.size(), 1);
        Assert.assertEquals(out.get(0).tcId(), "TC_01");
    }

    @Test
    public void keepsAllWhenStoryIncludesSuccessPath() {
        String story = INVALID_ONLY_STORY + " Also verify successful login reaches the News Feed.";
        ManualTestCase happy = new ManualTestCase(
                "TC_03", "Valid Credentials Check", "", "steps", "feed visible", "P1", "happy", "", "", "AUTOMATE");
        List<ManualTestCase> out = GeneratedTcScopeFilter.apply(story, List.of(happy));
        Assert.assertEquals(out.size(), 1);
    }
}
