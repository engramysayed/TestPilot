<#--
  Thin test: screen vars + chronological setup/body calls only.
  Auth: BaseMobileTest.@BeforeMethod → authSetupIfConfigured() (refs only).
-->
package project.tests.generated;

import org.testng.annotations.Test;
import project.tests.BaseMobileTest;
<#list screenImports![] as imp>
import ${imp};
</#list>

public class ${tcId} extends BaseMobileTest {

    @Test(description = "${testName!tcId}")
    public void run() {
${authSetupComment!}
<#list screenVars![] as v>
        ${v.type} ${v.name} = new ${v.type}((io.appium.java_client.android.AndroidDriver) getAndroidDriver());
</#list>
<#list setupCalls![] as c>
        ${c};
</#list>
<#list bodyCalls![] as c>
        ${c};
</#list>
    }
}
