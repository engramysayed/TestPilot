package project.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;
import project.utils.Actions.Act;
import project.utils.Logs.LogsManager;
import project.utils.reports.AllureSteps;
import project.validations.Validate;
<#if fields?filter(f -> f.smart)?has_content>
import project.utils.locators.KeelBy;
</#if>
<#if fields?filter(f -> !f.smart && !f.parameterized && !f.nlCached && f.bag?has_content)?has_content>
import project.utils.locators.FirstOf;
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
 * Generated page for ${stem} (fields + actions + assertions + isLoaded in one class).
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
<#if field.parameterized>
    /** Parameterized row/list locator factory. */
    protected By ${field.name}(String name) {
        return By.${field.strategy}(${field.value});
    }
<#elseif field.smart>
    /** Keel targets (contract v${field.contract}), main first then fallbacks. */
    protected final By ${field.name} = ${field.smartExpr};
<#elseif field.nlCached>
    /** NL-cached validated By: ${field.nlPhrase?j_string} */
    protected final By ${field.name} = By.${field.strategy}("${field.value?j_string}");
<#elseif field.bag?has_content>
    /** Primary first, then verified fallbacks (first locator that matches wins; fallback wins are logged). */
    protected final By ${field.name} = FirstOf.of("${field.name}",
            By.${field.strategy}("${field.value?j_string}")<#list field.bag as fb>,
            By.${fb.strategy}("${fb.value?j_string}")</#list>);
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
            Act.selectFromDD(${method.fieldName}, value);
<#else>
            Act.type(${method.fieldName}, value);
</#if>
        });
</#if>
    }
<#else>
    @Step("${method.name}")
    public void ${method.name}() {
<#if method.action == "drag">
        SettledActions.drag(driver, ${method.fieldName}, ${method.dropFieldName}, "${method.name}");
<#elseif method.smart>
        SettledActions.click(driver, ${method.fieldName}, "click", "${method.name}"<#if method.pointer>, true</#if>);
<#else>
        AllureSteps.run("${method.name}", () -> {
            Act.click(${method.fieldName});
        });
</#if>
    }
</#if>

</#list>
<#if loadedMarker?has_content>
    /** True when a stable page marker is visible (preferred hook / id / test id). */
    public boolean isLoaded() {
        try {
            return Act.findElement(${loadedMarker}).isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

</#if>
<#if loginHelper??>
    public void login(String username, String password) {
        ${loginHelper.userMethod}(username);
        ${loginHelper.passMethod}(password);
        ${loginHelper.submitMethod}();
    }

</#if>
<#list assertions as assertion>
<#if assertion.parameterized>
    @Step("${assertion.name}")
    public void ${assertion.name}(String expected) {
        AllureSteps.run("${assertion.name}", () -> {
<#if assertion.smart>
          try {
</#if>
<#if assertion.assertionType == "textContains">
            Validate.textContains(${assertion.fieldName}, expected);
<#elseif assertion.assertionType == "urlContains">
            Validate.urlContains(expected);
<#elseif assertion.assertionType == "checked" || assertion.assertionType == "selected">
            Validate.elementSelected(${assertion.fieldName}, expected);
<#elseif assertion.assertionType == "value" || assertion.assertionType == "valueEquals">
            Validate.elementValue(${assertion.fieldName}, expected);
<#elseif assertion.assertionType == "textEquals">
            Validate.elementTextEquals(${assertion.fieldName}, expected);
<#elseif assertion.assertionType == "ariaRole">
            Validate.elementAriaRole(${assertion.fieldName}, expected);
<#elseif assertion.assertionType == "ariaLabel">
            Validate.elementAriaLabel(${assertion.fieldName}, expected);
<#elseif assertion.assertionType == "region">
            Validate.elementRegion(${assertion.fieldName}, expected);
<#else>
            LogsManager.error("unsupported assertionType: ${assertion.assertionType}");
            Validate.softTrue(false, "unsupported assertionType: ${assertion.assertionType}");
</#if>
<#if assertion.smart>
          } finally {
              KeelLocate.reset(WebDriverFactory.getDriver());
          }
</#if>
        });
    }
<#else>
    @Step("${assertion.name}")
    public void ${assertion.name}() {
        AllureSteps.run("${assertion.name}", () -> {
<#if assertion.smart>
          try {
</#if>
<#if assertion.assertionType == "visible">
            Validate.elementVisable(${assertion.fieldName});
<#elseif assertion.assertionType == "unchecked">
            Validate.elementUnchecked(${assertion.fieldName});
<#elseif assertion.assertionType == "enabled">
            Validate.elementEnabled(${assertion.fieldName});
<#elseif assertion.assertionType == "disabled">
            Validate.elementDisabled(${assertion.fieldName});
<#elseif assertion.assertionType == "notVisible">
            Validate.elementNotVisible(${assertion.fieldName});
<#elseif assertion.assertionType == "captureText">
            Validate.captureFrom(${assertion.fieldName}, "${assertion.slot?j_string}", "${assertion.expected?j_string}");
<#elseif assertion.assertionType == "capturedEquals">
            Validate.compareCapturedExact(${assertion.fieldName}, "${assertion.slot?j_string}");
<#elseif assertion.assertionType == "signedOut">
            Validate.signedOut(${assertion.fieldName}, "${assertion.expected?j_string}");
<#else>
            LogsManager.error("unsupported assertionType: ${assertion.assertionType}");
            Validate.softTrue(false, "unsupported assertionType: ${assertion.assertionType}");
</#if>
<#if assertion.smart>
          } finally {
              KeelLocate.reset(WebDriverFactory.getDriver());
          }
</#if>
        });
    }
</#if>

</#list>
}
