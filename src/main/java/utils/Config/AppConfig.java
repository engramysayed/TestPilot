package utils.Config;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;

@LoadPolicy(LoadType.MERGE)
@Sources({
        "system:properties",
        "classpath:webapp.properties"
})
public interface AppConfig extends Config {

    //EDGE - CHROME
    @Key("BROWSER_TYPE")
    @DefaultValue("EDGE")
    String browserType();

    //LOCAL - HEADLESS - REMOTE
    @Key("EXECUTION_TYPE")
    @DefaultValue("LOCAL")
    String executionType();

    @Key("ISLOGIN")
    @DefaultValue("false")
    boolean isLogin();

    @Key("BASE_WEB")
    @DefaultValue("https://www.saucedemo.com/")
    String baseWeb();

    @Key("USERNAME")
    @DefaultValue("jeddah")
    String username();

    @Key("PASSWORD")
    @DefaultValue("12345678")
    String password();

    @Key("userNameLocator")
    @DefaultValue("cssSelector:input[placeholder='Username']")
    String userNameLocator();

    @Key("passwordLocator")
    @DefaultValue("cssSelector:input[placeholder='Password']")
    String passwordLocator();

    @Key("clickLocator")
    @DefaultValue("cssSelector:button[type='submit']")
    String clickLocator();

    //free model->gemini-3-flash-preview
    @Key("GEMINI_MODEL")
    @DefaultValue("gemini-2.5-flash")
    String geminiModel();

    @Key("HTML_MAX_CHARS")
    @DefaultValue("50000")
    int htmlMaxChars();

    @Key("DEFAULT_WAIT")
    @DefaultValue("5")
    int defaultWait();

    @Key("DEFAULT_SCREENSHOT_WAIT")
    @DefaultValue("1")
    int defaultScreenshotWait();

    @Key("MaxSteps")
    @DefaultValue("3")
    int maxSteps();

    @Key("MaxCycles")
    @DefaultValue("25")
    int maxCycles();

}
