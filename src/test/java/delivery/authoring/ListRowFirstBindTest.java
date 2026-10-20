package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class ListRowFirstBindTest {

    private static final String TWO_PENDING = """
            <html><body>
            <table>
              <tbody>
                <tr>
                  <td><a href="/users/1">Sara Ali</a></td>
                  <td>Pending Edit</td>
                </tr>
                <tr>
                  <td><a href="/users/2">Omar Nabil</a></td>
                  <td>Pending Edit</td>
                </tr>
              </tbody>
            </table>
            </body></html>
            """;

    private static final String TWO_LOGINS = """
            <html><body>
              <button type="submit">Login</button>
              <button type="button">Login</button>
            </body></html>
            """;

    @Test
    public void unquotedPendingStatus_clicksFirstRowNameLink() throws Exception {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Click on an Agent user with Pending edit status from the list");
        AuthoringService authoring = new AuthoringService(null, new LocatorValidator());
        List<ProvenStep> steps = authoring.authorIntent("TC_1", intent, TWO_PENDING, null, false);
        Assert.assertFalse(steps.isEmpty(), "expected a bound click");
        Assert.assertTrue(steps.get(0).validated(), steps.isEmpty() ? "" : steps.get(0).rationale());
        Assert.assertEquals(steps.get(0).action(), "click");
        String loc = steps.get(0).locatorValue();
        Assert.assertTrue(loc.contains("/users/1") || loc.contains("Sara Ali"),
                "first Pending Edit row should bind Sara Ali, got " + loc);
        Assert.assertFalse(loc.contains("/users/2") || loc.contains("Omar"), loc);
        Assert.assertTrue(steps.get(0).rationale().contains("listRowFirst"), steps.get(0).rationale());
    }

    @Test
    public void quotedName_clicksThatRowNotFirstPending() throws Exception {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Click on \"Omar Nabil\"");
        AuthoringService authoring = new AuthoringService(null, new LocatorValidator());
        List<ProvenStep> steps = authoring.authorIntent("TC_2", intent, TWO_PENDING, null, false);
        Assert.assertTrue(steps.get(0).validated(), steps.get(0).rationale());
        String loc = steps.get(0).locatorValue();
        Assert.assertTrue(loc.contains("/users/2") || loc.contains("Omar Nabil"),
                "quoted name should bind Omar, got " + loc);
        Assert.assertFalse(loc.contains("/users/1") || loc.contains("Sara"), loc);
    }

    @Test
    public void twoLoginButtons_areNotListRowFirst() throws Exception {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Click the Login button");
        AuthoringService authoring = new AuthoringService(null, new LocatorValidator());
        List<ProvenStep> steps = authoring.authorIntent("TC_3", intent, TWO_LOGINS, null, false);
        Assert.assertFalse(steps.get(0).validated() && steps.get(0).rationale().contains("listRowFirst"),
                "page Login twins must not use list-row-first");
    }
}
