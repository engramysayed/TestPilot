package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** r3 template properties: keel.settle.network.{scope,ignore,allow,effect}, defaults and the /regex/ spelling. */
public class SettleNetworkPropertiesTest {
    static final String[] KEYS = {"keel.settle.network.scope", "keel.settle.network.ignore", "keel.settle.network.allow",
            "keel.settle.network.effect"};
    private Class<?> settle;

    @BeforeClass
    public void compile() throws Exception {
        settle = CustomerTemplateCompiler.compile(Files.createTempDirectory("settle-net"),
                Path.of("customer-framework-template/src/main/java/project/utils/settle/Settle.java")).loadClass("project.utils.settle.Settle");
    }

    @AfterMethod(alwaysRun = true)
    public void clear() {
        for (String k : KEYS) System.clearProperty(k);
    }

    private Object call(String name, Object... args) throws Exception {
        Method m = name.equals("prop") ? settle.getDeclaredMethod(name, String.class, String.class) : settle.getDeclaredMethod(name, String.class);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    public void propertyNamesMatchTheSpec() throws Exception {
        Assert.assertEquals(settle.getField("NETWORK_SCOPE_PROPERTY").get(null), KEYS[0]);
        Assert.assertEquals(settle.getField("NETWORK_IGNORE_PROPERTY").get(null), KEYS[1]);
        Assert.assertEquals(settle.getField("NETWORK_ALLOW_PROPERTY").get(null), KEYS[2]);
        Assert.assertEquals(settle.getField("NETWORK_EFFECT_PROPERTY").get(null), KEYS[3]);
    }

    @Test
    public void defaultsArePageScopeAndCorroboratedEffect() throws Exception {
        Assert.assertEquals(call("prop", KEYS[0], "page"), "page");
        Assert.assertEquals(call("prop", KEYS[3], "corroborated"), "corroborated");
        Assert.assertEquals(call("csv", KEYS[1]), List.of());
        System.setProperty(KEYS[0], " all ");
        System.setProperty(KEYS[3], "alone");
        Assert.assertEquals(call("prop", KEYS[0], "page"), "all");
        Assert.assertEquals(call("prop", KEYS[3], "corroborated"), "alone");
    }

    @Test
    public void ignoreAcceptsSubstringsSlashRegexAndRePrefix() throws Exception {
        System.setProperty(KEYS[1], "/beacon, /\\/track\\?.*/ , re:^https://x\\.y/,,");
        Assert.assertEquals(call("csv", KEYS[1]), List.of("/beacon", "re:\\/track\\?.*", "re:^https://x\\.y/"));
        System.setProperty(KEYS[2], "https://api.example.com, https://cdn.example.com");
        Assert.assertEquals(call("csv", KEYS[2]), List.of("https://api.example.com", "https://cdn.example.com"));
    }
}
