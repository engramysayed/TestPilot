package project.pages;

import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;

/**
 * Generated page actions for ${stem}.
 */
public class ${actionsClassName} extends ${locatorsClassName} {

    public ${actionsClassName}(WebDriverFactory driver) {
        super(driver);
    }

<#list methods as method>
<#if method.needsValue>
    public void ${method.name}(String value) {
        AllureSteps.run("${method.name}", () -> {
<#if method.action == "select">
            driver.element().selectFromDD(${method.fieldName}, value);
<#else>
            driver.element().type(${method.fieldName}, value);
</#if>
        });
    }
<#else>
    public void ${method.name}() {
        AllureSteps.run("${method.name}", () -> {
            driver.element().click(${method.fieldName});
        });
    }
</#if>

</#list>
<#list assertions as assertion>
<#if assertion.parameterized>
    public void ${assertion.name}(String expected) {
        AllureSteps.run("${assertion.name}", () -> {
<#if assertion.assertionType == "textContains">
<#if assertion.fieldName?has_content>
            driver.validation().textContains(${assertion.fieldName}, expected);
<#else>
            driver.validation().bodyTextContains(expected);
</#if>
<#elseif assertion.assertionType == "urlContains">
            driver.validation().urlContains(expected);
<#elseif assertion.assertionType == "checked" || assertion.assertionType == "selected">
            driver.validation().elementSelected(${assertion.fieldName}, expected);
<#else>
            LogsManager.error("unsupported assertionType: ${assertion.assertionType}");
            driver.validation().softTrue(false, "unsupported assertionType: ${assertion.assertionType}");
</#if>
        });
    }
<#else>
    public void ${assertion.name}() {
        AllureSteps.run("${assertion.name}", () -> {
<#if assertion.assertionType == "visible">
            driver.validation().elementVisable(${assertion.fieldName});
<#elseif assertion.assertionType == "unchecked">
            driver.validation().elementUnchecked(${assertion.fieldName});
<#elseif assertion.assertionType == "notVisible">
            driver.validation().elementNotVisible(${assertion.fieldName});
<#elseif assertion.assertionType == "captureText">
            driver.validation().captureFrom(${assertion.fieldName}, "${assertion.slot?j_string}", "${assertion.expected?j_string}");
<#elseif assertion.assertionType == "capturedEquals">
            driver.validation().compareCapturedExact(${assertion.fieldName}, "${assertion.slot?j_string}");
<#elseif assertion.assertionType == "signedOut">
            driver.validation().signedOut(${assertion.fieldName}, "${assertion.expected?j_string}");
<#else>
            LogsManager.error("unsupported assertionType: ${assertion.assertionType}");
            driver.validation().softTrue(false, "unsupported assertionType: ${assertion.assertionType}");
</#if>
        });
    }
</#if>

</#list>
}
