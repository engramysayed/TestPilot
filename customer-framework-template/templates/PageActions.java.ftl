package project.pages;

import io.qameta.allure.Step;
import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;

/**
 * Generated page actions for ${stem}.
 */
public class ${actionsClassName} extends ${locatorsClassName} {

    public ${actionsClassName}(WebDriverFactory driver) {
        super(driver);
    }

<#list methods as method>
<#if method.needsValue>
    @Step("${method.name}")
    public void ${method.name}(String value) {
<#if method.action == "select">
        driver.element().selectFromDD(${method.fieldName}, value);
<#else>
        driver.element().type(${method.fieldName}, value);
</#if>
    }
<#else>
    @Step("${method.name}")
    public void ${method.name}() {
        driver.element().click(${method.fieldName});
    }
</#if>

</#list>
<#list assertions as assertion>
    @Step("${assertion.name}")
    public void ${assertion.name}() {
<#if assertion.assertionType == "visible">
        driver.validation().elementVisable(${assertion.fieldName});
<#elseif assertion.assertionType == "textContains">
        driver.validation().bodyTextContains("${assertion.expected?j_string}");
<#elseif assertion.assertionType == "urlContains">
        driver.validation().urlContains("${assertion.expected?j_string}");
<#elseif assertion.assertionType == "checked" || assertion.assertionType == "selected">
        driver.validation().elementSelected(${assertion.fieldName}, "${assertion.expected?j_string}");
<#elseif assertion.assertionType == "unchecked">
        driver.validation().elementUnchecked(${assertion.fieldName});
<#elseif assertion.assertionType == "notVisible">
        driver.validation().elementNotVisible(${assertion.fieldName});
<#else>
        LogsManager.error("unsupported assertionType: ${assertion.assertionType}");
        driver.validation().softTrue(false, "unsupported assertionType: ${assertion.assertionType}");
</#if>
    }

</#list>
}
