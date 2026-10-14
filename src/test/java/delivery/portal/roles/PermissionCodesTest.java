package delivery.portal.roles;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

public class PermissionCodesTest {
    @Test
    public void featureCodesArePrimaryAndJobAliasesNormalize() {
        Assert.assertEquals(PermissionCodes.normalize("job.generate").orElseThrow(),
                PermissionCodes.FEATURE_GENERATE);
        Assert.assertEquals(PermissionCodes.normalize("feature.execute").orElseThrow(),
                PermissionCodes.FEATURE_EXECUTE);
        Assert.assertEquals(PermissionCodes.forJobKind("HUNT"), PermissionCodes.FEATURE_HUNT);
        Assert.assertEquals(PermissionCodes.forJobKind("AUTOMATE"), PermissionCodes.FEATURE_AUTOMATE);
        Set<String> n = PermissionCodes.normalizeAll(List.of("job.hunt", "feature.generate"));
        Assert.assertTrue(n.contains(PermissionCodes.FEATURE_HUNT));
        Assert.assertTrue(n.contains(PermissionCodes.FEATURE_GENERATE));
        Assert.assertFalse(n.contains("job.hunt"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void unknownCodeRejected() {
        PermissionCodes.requireKnown("not.a.real.permission");
    }

    @Test
    public void creditsSelfIsAccountCreditsNotMeCredits() {
        Assert.assertTrue(PermissionCodes.all().contains(PermissionCodes.CREDITS_VIEW_SELF));
        Assert.assertFalse(PermissionCodes.all().contains("me.credits"));
    }
}
