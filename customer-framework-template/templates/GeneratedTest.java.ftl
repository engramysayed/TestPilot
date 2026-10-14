package project.tests.generated;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import project.tests.BaseTest;
<#if readOnly!false>
import project.utils.retry.KeelReadOnly;
import project.utils.retry.KeelRetryAnalyzer;
</#if>
<#list pageImports as cls>
import project.pages.${cls};
</#list>

public class ${className} extends BaseTest {

<#include "test-before-method.ftl">
<#if readOnly!false>
    @KeelReadOnly
    @Test(description = "${testDescription?j_string}", groups = {KeelReadOnly.GROUP}, retryAnalyzer = KeelRetryAnalyzer.class)
<#else>
    @Test(description = "${testDescription?j_string}")
</#if>
    public void ${methodName}() {
<#if chronCalls?size == 0>
        org.testng.Assert.fail("No proven steps generated for ${tcId?j_string}");
<#else>
<#list pageVars as p>
        ${p.className} ${p.varName} = new ${p.className}(driver);
</#list>
<#list chronCalls as call>
<#include "test-call-site.ftl">
</#list>
</#if>
<#if reviewComments??>
<#list reviewComments as note>
        // REVIEW: ${note?j_string}
</#list>
</#if>
    }
}
