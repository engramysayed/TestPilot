package delivery.codegen;

import io.qameta.allure.Allure;
import io.qameta.allure.model.TestResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Parameter-safe Allure reporting must wrap the real action and mark the named step failed
 * when the action throws or a soft assertion is recorded.
 */
public class AllureStepsReportingTest {
    private static final Path ALLURE_STEPS = Path.of(
            "customer-framework-template/src/main/java/project/utils/reports/AllureSteps.java");
    private static final Path VALIDATION = Path.of(
            "customer-framework-template/src/main/java/project/validations/Validation.java");

    private Path resultsDir;
    private String previousResultsDir;
    private ClassLoader loader;
    private Method run;
    private Method validationReset;
    private Method pendingFailureCount;

    @BeforeClass
    public void compileAndPointAllure() throws Exception {
        Assert.assertTrue(Files.isRegularFile(ALLURE_STEPS), "AllureSteps.java must ship in the template");
        resultsDir = Files.createTempDirectory("allure-steps-results");
        previousResultsDir = System.getProperty("allure.results.directory");
        System.setProperty("allure.results.directory", resultsDir.toString());
        Path classes = Files.createTempDirectory("allure-steps-classes");
        loader = CustomerTemplateCompiler.compile(classes, ALLURE_STEPS, VALIDATION);
        Class<?> steps = loader.loadClass("project.utils.reports.AllureSteps");
        run = steps.getMethod("run", String.class, Runnable.class);
        Class<?> validation = loader.loadClass("project.validations.Validation");
        validationReset = validation.getMethod("reset");
        pendingFailureCount = validation.getMethod("pendingFailureCount");
    }

    @AfterMethod
    public void resetValidation() throws Exception {
        if (validationReset != null) {
            validationReset.invoke(null);
        }
    }

    @AfterClass(alwaysRun = true)
    public void restoreResultsDir() {
        if (previousResultsDir == null) {
            System.clearProperty("allure.results.directory");
        } else {
            System.setProperty("allure.results.directory", previousResultsDir);
        }
    }

    @Test
    public void thrownActionMarksNamedStepFailed() throws Exception {
        String uuid = startCase("thrown-type");
        try {
            run.invoke(null, "type_Secret", (Runnable) () -> {
                throw new IllegalStateException("dead session");
            });
            Assert.fail("action exception must propagate");
        } catch (InvocationTargetException ex) {
            Assert.assertTrue(ex.getCause() instanceof IllegalStateException, String.valueOf(ex.getCause()));
        } finally {
            finishCase(uuid);
        }
        JSONObject step = requireStep("type_Secret");
        Assert.assertEquals(step.getString("status"), "failed", step.toString());
        Assert.assertTrue(step.getLong("stop") >= step.getLong("start"), step.toString());
        Assert.assertFalse(step.toString().contains("CANARY_PW_zipreplay_7f2c9a"), step.toString());
        Assert.assertFalse(step.has("parameters") && step.getJSONArray("parameters").length() > 0,
                "typed values must not be Allure step parameters: " + step);
    }

    @Test
    public void softAssertionMarksNamedStepFailedWithoutThrowing() throws Exception {
        Object validation = loader.loadClass("project.validations.Validation")
                .getConstructor(org.openqa.selenium.WebDriver.class)
                .newInstance(new Object[]{null});
        Method softTrue = validation.getClass().getMethod("softTrue", boolean.class, String.class);
        String uuid = startCase("soft-assert");
        try {
            run.invoke(null, "assert_Body_Text_Contains", (Runnable) () -> {
                try {
                    softTrue.invoke(validation, false, "CANARY_ASSERT_zipreplay_7f2c9a");
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            finishCase(uuid);
        }
        Assert.assertEquals(pendingFailureCount.invoke(null), 1,
                "soft failure must remain pending for assertAll");
        JSONObject step = requireStep("assert_Body_Text_Contains");
        Assert.assertEquals(step.getString("status"), "failed",
                "soft assertion must not leave a passed Allure step: " + step);
        Assert.assertTrue(step.getLong("stop") >= step.getLong("start"), step.toString());
    }

    @Test
    public void successfulActionMarksNamedStepPassedWithDuration() throws Exception {
        String uuid = startCase("click-ok");
        try {
            run.invoke(null, "click_Save_Button", (Runnable) () -> { });
        } finally {
            finishCase(uuid);
        }
        JSONObject step = requireStep("click_Save_Button");
        Assert.assertEquals(step.getString("status"), "passed", step.toString());
        Assert.assertTrue(step.getLong("stop") >= step.getLong("start"), step.toString());
        Assert.assertEquals(pendingFailureCount.invoke(null), 0);
    }

    private static String startCase(String name) {
        String uuid = UUID.randomUUID().toString();
        Allure.getLifecycle().scheduleTestCase(new TestResult().setUuid(uuid).setName(name));
        Allure.getLifecycle().startTestCase(uuid);
        return uuid;
    }

    private static void finishCase(String uuid) {
        Allure.getLifecycle().stopTestCase(uuid);
        Allure.getLifecycle().writeTestCase(uuid);
    }

    private JSONObject requireStep(String name) throws Exception {
        try (var stream = Files.walk(resultsDir)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith("-result.json")).toList()) {
                JSONObject json = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
                JSONObject found = findStep(json.optJSONArray("steps"), name);
                if (found != null) {
                    return found;
                }
            }
        }
        Assert.fail("missing Allure step " + name + " under " + resultsDir
                + " files=" + Files.list(resultsDir).map(Path::getFileName).toList());
        return null;
    }

    private static JSONObject findStep(JSONArray steps, String name) {
        if (steps == null) {
            return null;
        }
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            if (name.equals(step.optString("name"))) {
                return step;
            }
            JSONObject nested = findStep(step.optJSONArray("steps"), name);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
