package project.pages;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;
<#if fields?filter(f -> f.smart)?has_content>
import project.utils.locators.KeelBy;
</#if>
<#assign maxContract = 1>
<#list fields as f><#if f.contract gt maxContract><#assign maxContract = f.contract></#if></#list>
<#if maxContract gt 1 || assertions?filter(a -> a.smart)?has_content>
import project.utils.locators.KeelLocate;
</#if>
<#if methods?filter(m -> m.secret)?has_content>
import project.utils.reports.Secrets;
</#if>
<#if methods?filter(m -> m.smart || m.action == "drag")?has_content>
import project.utils.locators.SettledActions;
</#if>

/**
 * Generated page for ${stem} (locators + actions + assertions in one class).
 */
public class ${pageClassName} {
<#if maxContract gt 1>
    static {
        // fails fast with a clear message if the framework runtime is older than these locators
        KeelLocate.requireContract(${maxContract});
    }

</#if>
    protected final WebDriverFactory driver;

    public ${pageClassName}(WebDriverFactory driver) {
        this.driver = driver;
    }

<#list fields as field>
<#if field.smart>
    /** Keel targets (contract v${field.contract}), main first then fallbacks. */
    protected final By ${field.name} = ${field.smartExpr};
<#else>
    protected final By ${field.name} = By.${field.strategy}("${field.value?j_string}");
</#if>
</#list>

<#list methods as method>
<#if method.needsValue>
    public void ${method.name}(String value) {
<#if method.secret>
        Secrets.register(value); // IR secret: true - redacted from logs, Allure and keel-results jsonl
</#if>
<#if method.smart>
<#if method.action == "select">
        SettledActions.select(driver, ${method.fieldName}, value, "${method.settleAction}", "${method.name}");
<#else>
        SettledActions.type(driver, ${method.fieldName}, value, "${method.settleAction}", "${method.name}");
</#if>
<#else>
        AllureSteps.run("${method.name}", () -> {
<#if method.action == "select">
            driver.element().selectFromDD(${method.fieldName}, value);
<#else>
            driver.element().type(${method.fieldName}, value);
</#if>
        });
</#if>
    }
<#else>
    public void ${method.name}() {
<#if method.action == "drag">
        SettledActions.drag(driver, ${method.fieldName}, ${method.dropFieldName}, "${method.name}");
<#elseif method.smart>
        SettledActions.click(driver, ${method.fieldName}, "click", "${method.name}"<#if method.pointer>, true</#if>);
<#else>
        AllureSteps.run("${method.name}", () -> {
            driver.element().click(${method.fieldName});
        });
</#if>
    }
</#if>

</#list>
<#if loginHelper??>
    /** Preferred login API: type credentials then submit. */
    public void login(String username, String password) {
        ${loginHelper.userMethod}(username);
        ${loginHelper.passMethod}(password);
        ${loginHelper.submitMethod}();
    }

</#if>
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
