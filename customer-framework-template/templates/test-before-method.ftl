<#-- Prerequisite login/setup: local page vars inside helper; @BeforeMethod = super + helper only. -->
<#assign hasBefore = (needsLoginBeforeMethod!false) || (setupChronCalls?size > 0)>
<#if hasBefore>
    private void ${prerequisiteMethod}() {
<#list beforePageVars as p>
        ${p.className} ${p.varName} = new ${p.className}(driver);
</#list>
<#list beforeCalls as call>
<#include "test-call-site.ftl">
</#list>
    }

    @BeforeMethod
    @Override
    public void setUp() {
        super.setUp();
        ${prerequisiteMethod}();
        project.validations.Validation.assertAll();
    }

</#if>
