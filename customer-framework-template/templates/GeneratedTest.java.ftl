package project.tests.generated;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import project.drivers.WebDriverFactory;
import project.tests.BaseTest;
import project.utils.dataReader.PropertyReader;
import project.validations.Validation;
<#list pageImports as cls>
import project.pages.${cls};
</#list>

public class ${className} extends BaseTest {

    @BeforeMethod
    public void setUp() {
        PropertyReader.loadProperties();
        driver = new WebDriverFactory();
        String base = PropertyReader.getProperty("BASE_WEB");
        if (base != null && !base.isBlank()) {
            driver.browser().navigateToUrl(base);
        }
<#if needsLoginBeforeMethod!false>
<#list loginPageVars as p>
        ${p.className} ${p.varName} = new ${p.className}(driver);
</#list>
<#list loginChronCalls as call>
<#if call.propKey?has_content>
        ${call.pageVar}.${call.method}(nullToEmpty(PropertyReader.getProperty("${call.propKey?j_string}")));
<#elseif call.needsValue>
        ${call.pageVar}.${call.method}("${call.value?j_string}");
<#else>
        ${call.pageVar}.${call.method}();
</#if>
</#list>
</#if>
    }

    @AfterMethod
    public void tearDown() {
        Validation.assertAll();
        if (driver != null) {
            driver.quit();
        }
    }

    @Test(description = "${testDescription?j_string}")
    public void ${methodName}() {
<#if chronCalls?size == 0>
        org.testng.Assert.fail("No proven steps generated for ${tcId?j_string}");
<#else>
<#list pageVars as p>
        ${p.className} ${p.varName} = new ${p.className}(driver);
</#list>
<#list chronCalls as call>
<#if call.propKey?has_content>
        ${call.pageVar}.${call.method}(nullToEmpty(PropertyReader.getProperty("${call.propKey?j_string}")));
<#elseif call.needsValue>
        ${call.pageVar}.${call.method}("${call.value?j_string}");
<#else>
        ${call.pageVar}.${call.method}();
</#if>
</#list>
</#if>
<#if reviewComments??>
<#list reviewComments as note>
        // REVIEW: ${note?j_string}
</#list>
</#if>
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
