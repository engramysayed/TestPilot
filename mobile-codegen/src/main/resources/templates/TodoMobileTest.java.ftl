package project.tests.todo;

import org.testng.annotations.Test;
import project.tests.BaseMobileTest;
<#list screenImports![] as imp>
import ${imp};
</#list>

/** Demoted / incomplete case — ${reason!"TODO"}. */
public class ${tcId} extends BaseMobileTest {

    @Test(description = "${testName!tcId}", enabled = false)
    public void run() {
${authSetupComment!}
        org.testng.Assert.fail("TODO: ${reason!"incomplete mobile case"}");
    }
}
