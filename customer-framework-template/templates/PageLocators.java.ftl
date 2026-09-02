package project.pages;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;

/**
 * Generated locator holders for ${stem}.
 */
public class ${locatorsClassName} {
    protected final WebDriverFactory driver;

    public ${locatorsClassName}(WebDriverFactory driver) {
        this.driver = driver;
    }

<#list fields as field>
    protected final By ${field.name} = By.${field.strategy}("${field.value?j_string}");
</#list>
}
