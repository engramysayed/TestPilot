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
        Validation.clearCapturedPhrases();
        PropertyReader.loadProperties();
        driver = new WebDriverFactory();
        String base = PropertyReader.getProperty("BASE_WEB");
        if (base != null && !base.isBlank()) {
            driver.browser().navigateToUrl(base);
        }
<#list beforePageVars as p>
        ${p.className} ${p.varName} = new ${p.className}(driver);
</#list>
<#if needsLoginBeforeMethod!false>
<#list loginChronCalls as call>
<#if call.propKey?has_content>
        ${call.pageVar}.${call.method}(requiredProperty("${call.propKey?j_string}"));
<#elseif call.needsValue>
        ${call.pageVar}.${call.method}("${call.value?j_string}");
<#else>
        ${call.pageVar}.${call.method}();
</#if>
</#list>
</#if>
<#list setupChronCalls as call>
<#if call.propKey?has_content>
        ${call.pageVar}.${call.method}(requiredProperty("${call.propKey?j_string}"));
<#elseif call.needsValue>
        ${call.pageVar}.${call.method}("${call.value?j_string}");
<#else>
        ${call.pageVar}.${call.method}();
</#if>
</#list>
        Validation.assertAll();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            Validation.assertAll();
        } finally {
            Validation.clearCapturedPhrases();
            if (driver != null) {
                driver.quit();
            }
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
        ${call.pageVar}.${call.method}(requiredProperty("${call.propKey?j_string}"));
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

    private static String requiredProperty(String key) {
        String v = PropertyReader.getProperty(key);
        if (v == null) {
            throw new IllegalStateException("Missing required test data key: " + key);
        }
        return v;
    }
}
