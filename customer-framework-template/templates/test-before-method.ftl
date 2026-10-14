<#-- Optional @BeforeMethod override: super.setUp() then login/setup chron calls. -->
<#assign hasBefore = (needsLoginBeforeMethod!false) || (setupChronCalls?size > 0)>
<#if hasBefore>
    @BeforeMethod
    @Override
    public void setUp() {
        super.setUp();
<#list beforePageVars as p>
        ${p.className} ${p.varName} = new ${p.className}(driver);
</#list>
<#if needsLoginBeforeMethod!false>
<#list loginChronCalls as call>
<#include "test-call-site.ftl">
</#list>
</#if>
<#list setupChronCalls as call>
<#include "test-call-site.ftl">
</#list>
        project.validations.Validation.assertAll();
    }

</#if>
