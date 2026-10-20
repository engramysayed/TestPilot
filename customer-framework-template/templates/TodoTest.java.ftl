package project.tests.todo;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import project.tests.BaseTest;
import project.utils.dataReader.PropertyReader;
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

<#include "test-before-method.ftl">
    @Test(description = "${testDescription?j_string}", enabled = false)
    public void ${methodName}() {
<#list pageFields as p>
        ${p.varName} = new ${p.className}(driver);
</#list>
<#list chronCalls as call>
<#include "test-call-site.ftl">
</#list>
<#if reviewComments??>
<#list reviewComments as note>
        // REVIEW: ${note?j_string}
</#list>
</#if>
        // STOPPED HERE: ${reason?j_string}
        org.testng.Assert.fail("TODO ${tcId?j_string}: ${reason?j_string}");
    }
}
