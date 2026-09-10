package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

public class CallBeforeTest {

    @Test
    public void parseEmptyOrBlankReturnsEmptyList() {
        Assert.assertEquals(CallBefore.parse(null), List.of());
        Assert.assertEquals(CallBefore.parse(""), List.of());
        Assert.assertEquals(CallBefore.parse("  "), List.of());
        Assert.assertEquals(CallBefore.parse(" , , "), List.of());
    }

    @Test
    public void parseTrimsAndSplitsOnComma() {
        Assert.assertEquals(CallBefore.parse(" TC_01 , TC_02 "), List.of("TC_01", "TC_02"));
    }

    @Test
    public void parsePreservesOrderAndRepeats() {
        Assert.assertEquals(CallBefore.parse("TC_01,TC_00,TC_01"), List.of("TC_01", "TC_00", "TC_01"));
    }

    @Test
    public void formatJoinsWithComma() {
        Assert.assertEquals(CallBefore.format(List.of("TC_01", "TC_02")), "TC_01,TC_02");
    }

    @Test
    public void formatEmptyReturnsEmptyString() {
        Assert.assertEquals(CallBefore.format(null), "");
        Assert.assertEquals(CallBefore.format(List.of()), "");
    }

    @Test
    public void roundTrip() {
        String raw = "TC_01,TC_02";
        Assert.assertEquals(CallBefore.format(CallBefore.parse(raw)), raw);
    }

    @Test
    public void validateRefs_acceptsKnownIds() {
        CallBefore.validateRefs("TC_01,TC_02", "TC_06", Set.of("TC_01", "TC_02", "TC_06"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void validateRefs_rejectsSelf() {
        try {
            CallBefore.validateRefs("TC_06", "TC_06", Set.of("TC_06"));
            Assert.fail("Expected self rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "CALL_BEFORE_SELF: TC_06");
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void validateRefs_rejectsUnknown() {
        try {
            CallBefore.validateRefs("TC_99", "TC_06", Set.of("TC_06"));
            Assert.fail("Expected unknown rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "UNKNOWN_CALL_BEFORE: TC_99");
            throw e;
        }
    }
}
