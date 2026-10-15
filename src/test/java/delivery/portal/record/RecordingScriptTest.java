package delivery.portal.record;

import org.testng.Assert;
import org.testng.annotations.Test;

public class RecordingScriptTest {

    @Test
    public void hookListensForClicksAndMasksPasswords() {
        String script = RecordingScript.install();
        Assert.assertTrue(script.contains("addEventListener"));
        Assert.assertTrue(script.contains("password"));
        Assert.assertTrue(script.contains("keel-record"));
        Assert.assertTrue(RecordingScript.READ.contains("__keelRec"));
    }
}
