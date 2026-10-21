package project.tests.generated;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import project.tests.BaseTest;
import project.utils.dataReader.PropertyReader;
<#assign multi = (testMethods?size > 1)>
<#assign first = testMethods[0]>
<#if anyReadOnly!false>
import project.utils.retry.KeelReadOnly;
import project.utils.retry.KeelRetryAnalyzer;
</#if>
<#list pageImports as cls>
import project.pages.${cls};
</#list>

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
<#if tm.readOnly!false>
    @KeelReadOnly
    @Test(description = "${tm.testDescription?j_string}", groups = {KeelReadOnly.GROUP}, retryAnalyzer = KeelRetryAnalyzer.class)
<#else>
    @Test(description = "${tm.testDescription?j_string}")
</#if>
    public void ${tm.methodName}() {
<#if multi && (tm.beforeCalls?size gt 0)>
        ${tm.prerequisiteMethod}();
</#if>
<#if tm.chronCalls?size == 0>
        org.testng.Assert.fail("No proven steps generated for ${tm.tcId?j_string}");
<#else>
<#list tm.pageFields as p>
        ${p.varName} = new ${p.className}(driver);
</#list>
<#list tm.chronCalls as call>
<#include "test-call-site.ftl">
</#list>
</#if>
<#if tm.reviewComments??>
<#list tm.reviewComments as note>
        // REVIEW: ${note?j_string}
</#list>
</#if>
<#if multi>
        project.validations.Validation.assertAll();
</#if>
    }

</#list>
}
