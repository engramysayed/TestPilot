<#--
  ONE screen = ONE class (locators + actions + assertions + captures together).
  Do NOT emit *_Locators / *_Actions. Aligns Maestro + web P2.
  M4-08: scrollUntil / swipe call SettledMobileActions (never tap).
-->
package ${packageName!"project.screens"};

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
<#assign needsFirstOf = false>
<#list locators![] as loc>
<#if loc.fallbacks?has_content>
<#assign needsFirstOf = true>
</#if>
</#list>
<#if needsFirstOf>
import project.utils.locators.MobileFirstOf;
</#if>
import project.runtime.assertions.MobileAssertions;
import project.runtime.settle.SettledMobileActions;

/**
 * Generated screen: ${screenClassName}.
 */
public class ${screenClassName} {

    protected final AndroidDriver driver;

<#list locators![] as loc>
<#if loc.fallbacks?has_content>
    /** Primary first, then verified fallbacks (first locator that matches wins; outcomes logged per slot). */
    protected final By ${loc.fieldName} = MobileFirstOf.of("${screenClassName}", "${loc.fieldName}",
            ${loc.byExpression}<#list loc.fallbacks as fb>,
            ${fb}</#list>);
<#else>
    protected final By ${loc.fieldName} = ${loc.byExpression};
</#if>
</#list>

    public ${screenClassName}(AndroidDriver driver) {
        this.driver = driver;
    }

<#list actions![] as act>
<#if act.utilMethod == "scrollUntil">
    public void ${act.methodName}(String direction, int maxSwipes) {
        SettledMobileActions.scrollUntil(driver, ${act.locatorField}, direction, maxSwipes);
    }

<#elseif act.utilMethod == "swipe">
    public void ${act.methodName}(String direction) {
        SettledMobileActions.swipe(driver, direction);
    }

<#elseif act.utilMethod == "typeSecret">
    public void ${act.methodName}(<#if act.hasArg>${act.argType} ${act.argName}</#if>) {
        SettledMobileActions.typeSecret(driver, ${act.locatorField}, ${act.argName});
    }

<#elseif (act.passSettleKind)!true>
    public void ${act.methodName}(<#if act.hasArg>${act.argType} ${act.argName}</#if>) {
        SettledMobileActions.${act.utilMethod}(driver, ${act.locatorField}<#if act.hasArg>, ${act.argName}</#if>, "${act.settleKind}");
    }

<#else>
    public void ${act.methodName}(<#if act.hasArg>${act.argType} ${act.argName}</#if>) {
        SettledMobileActions.${act.utilMethod}(driver, ${act.locatorField}<#if act.hasArg>, ${act.argName}</#if>);
    }

</#if>
</#list>
<#list asserts![] as a>
    public void ${a.methodName}(<#if a.hasArg>${a.argType} ${a.argName}</#if>) {
        MobileAssertions.${a.utilMethod}(driver, ${a.locatorField}<#if a.hasArg>, ${a.argName}</#if>);
    }

</#list>
<#list captures![] as c>
<#if c.compare>
    public void ${c.methodName}() {
        MobileAssertions.compareCaptured(driver, ${c.locatorField}, "${c.captureVar}", "${c.compareMode}");
    }
<#else>
    public String ${c.methodName}() {
        return MobileAssertions.captureText(driver, ${c.locatorField}, "${c.captureVar}");
    }
</#if>

</#list>
}
