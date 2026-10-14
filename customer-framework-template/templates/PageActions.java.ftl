package project.pages;

import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;
<#if methods?filter(m -> m.secret)?has_content>
import project.utils.reports.Secrets;
</#if>
<#if methods?filter(m -> m.smart || m.action == "drag")?has_content || assertions?filter(a -> a.smart)?has_content>
import project.utils.locators.KeelLocate;
import project.utils.locators.SmartWait;
</#if>
<#if methods?filter(m -> m.smart || m.action == "drag")?has_content>
import project.utils.settle.Settle;
</#if>
<#if methods?filter(m -> m.pointer || m.action == "drag")?has_content>
import project.utils.locators.KeelActions;
</#if>

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
<#if method.secret>
        Secrets.register(value); // IR secret: true - redacted from logs, Allure and keel-results jsonl
</#if>
        AllureSteps.run("${method.name}", () -> {
<#if method.smart>
            try {
                Settle.after(driver.get(), "${method.settleAction}", ${method.fieldName}, () -> {
                    SmartWait.untilActionable(driver.get(), ${method.fieldName});
<#if method.action == "select">
                    driver.element().selectFromDD(${method.fieldName}, value);
<#else>
                    driver.element().type(${method.fieldName}, value);
</#if>
                });
            } finally {
                KeelLocate.reset(driver.get());
            }
<#else>
<#if method.action == "select">
            driver.element().selectFromDD(${method.fieldName}, value);
<#else>
            driver.element().type(${method.fieldName}, value);
</#if>
</#if>
        });
    }
<#else>
    public void ${method.name}() {
        AllureSteps.run("${method.name}", () -> {
<#if method.action == "drag">
            try {
                Settle.after(driver.get(), "drag", ${method.fieldName}, () -> {
                    SmartWait.untilActionable(driver.get(), ${method.fieldName});
                    KeelActions.drag(driver.get(), ${method.fieldName}, ${method.dropFieldName});
                });
            } finally {
                KeelLocate.reset(driver.get());
            }
<#elseif method.smart>
            try {
                Settle.after(driver.get(), "click", ${method.fieldName}, () -> {
                    SmartWait.untilActionable(driver.get(), ${method.fieldName});
<#if method.pointer>
                    KeelActions.click(driver.get(), ${method.fieldName});
<#else>
                    driver.element().click(${method.fieldName});
</#if>
                });
            } finally {
                KeelLocate.reset(driver.get());
            }
<#else>
            driver.element().click(${method.fieldName});
</#if>
        });
    }
</#if>

</#list>
<#list assertions as assertion>
<#if assertion.parameterized>
    public void ${assertion.name}(String expected) {
        AllureSteps.run("${assertion.name}", () -> {
<#if assertion.smart>
          try {
</#if>
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
<#if assertion.smart>
          } finally {
              KeelLocate.reset(driver.get());
          }
</#if>
        });
    }
<#else>
    public void ${assertion.name}() {
        AllureSteps.run("${assertion.name}", () -> {
<#if assertion.smart>
          try {
</#if>
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
<#if assertion.smart>
          } finally {
              KeelLocate.reset(driver.get());
          }
</#if>
        });
    }
</#if>

</#list>
}
