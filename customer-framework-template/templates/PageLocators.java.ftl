package project.pages;

import org.openqa.selenium.By;
<#if fields?filter(f -> f.smart)?has_content>
import project.utils.locators.KeelBy;
</#if>
<#assign maxContract = 1>
<#list fields as f><#if f.contract gt maxContract><#assign maxContract = f.contract></#if></#list>
<#if maxContract gt 1>
import project.utils.locators.KeelLocate;
</#if>
import project.drivers.WebDriverFactory;

/**
 * Generated locator holders for ${stem}.
 */
public class ${locatorsClassName} {
<#if maxContract gt 1>
    static {
        // fails fast with a clear message if the framework runtime is older than these locators
        KeelLocate.requireContract(${maxContract});
    }

</#if>
    protected final WebDriverFactory driver;

    public ${locatorsClassName}(WebDriverFactory driver) {
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
}
