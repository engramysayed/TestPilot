package project.tests.todo;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import project.tests.BaseTest;
import project.utils.dataReader.PropertyReader;
<#assign multi = (testMethods?size > 1)>
<#assign first = testMethods[0]>
<#list pageImports as cls>
import project.pages.${cls};
</#list>

/**
 * Partial conversion result: proven steps kept; conversion stopped at the commented failure.
 */
public class ${className} extends BaseTest {

<#list pageFields as p>
    private ${p.className} ${p.varName};
</#list>

<#list testMethods as tm>
<#if tm.beforeCalls?size gt 0>
    private void ${tm.prerequisiteMethod}() {
<#list tm.beforePageVars as p>
        ${p.className} ${p.varName} = new ${p.className}(driver);
</#list>
<#list tm.beforeCalls as call>
<#include "test-call-site.ftl">
</#list>
    }

</#if>
</#list>
<#if !multi && ((first.needsLoginBeforeMethod!false) || (first.beforeCalls?size gt 0))>
    @BeforeMethod
    @Override
    public void setUp() {
        super.setUp();
        ${first.prerequisiteMethod}();
        project.validations.Validation.assertAll();
    }

</#if>
<#list testMethods as tm>
    @Test(description = "${tm.testDescription?j_string}", enabled = false)
    public void ${tm.methodName}() {
<#if multi && (tm.beforeCalls?size gt 0)>
        ${tm.prerequisiteMethod}();
</#if>
<#list tm.pageFields as p>
        ${p.varName} = new ${p.className}(driver);
</#list>
<#list tm.chronCalls as call>
<#include "test-call-site.ftl">
</#list>
<#if tm.reviewComments??>
<#list tm.reviewComments as note>
        // REVIEW: ${note?j_string}
</#list>
</#if>
        // STOPPED HERE: ${tm.reason?j_string}
        org.testng.Assert.fail("TODO ${tm.tcId?j_string}: ${tm.reason?j_string}");
<#if multi>
        project.validations.Validation.assertAll();
</#if>
    }

</#list>
}
