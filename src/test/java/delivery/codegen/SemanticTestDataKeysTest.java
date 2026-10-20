package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SemanticTestDataKeysTest {

    @Test
    public void prefixesWhenEnvSet() {
        Assert.assertEquals(SemanticTestDataKeys.mapPlaceholder("${TARGET_USERNAME}", "sit"), "sit_username");
        Assert.assertEquals(SemanticTestDataKeys.mapPlaceholder("${TARGET_PASSWORD}", "SIT"), "sit_password");
    }

    @Test
    public void noPrefixWhenEnvBlank() {
        Assert.assertEquals(SemanticTestDataKeys.mapPlaceholder("${TARGET_USERNAME}", ""), "username");
        Assert.assertEquals(SemanticTestDataKeys.mapPlaceholder("${TARGET_PASSWORD}", null), "password");
    }
}
