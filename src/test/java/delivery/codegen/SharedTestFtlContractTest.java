package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** 0059: Generated/Todo share call-site and before-method includes (no drift). */
public class SharedTestFtlContractTest {
    private static final Path GEN = Path.of("customer-framework-template/templates/GeneratedTest.java.ftl");
    private static final Path TODO = Path.of("customer-framework-template/templates/TodoTest.java.ftl");
    private static final Path CALL = Path.of("customer-framework-template/templates/test-call-site.ftl");
    private static final Path BEFORE = Path.of("customer-framework-template/templates/test-before-method.ftl");

    @Test
    public void generatedAndTodoIncludeSharedMacros() throws Exception {
        String gen = Files.readString(GEN);
        String todo = Files.readString(TODO);
        Assert.assertTrue(Files.isRegularFile(CALL), "test-call-site.ftl missing");
        Assert.assertTrue(Files.isRegularFile(BEFORE), "test-before-method.ftl missing");
        Assert.assertTrue(gen.contains("<#include \"test-before-method.ftl\">"), gen);
        Assert.assertTrue(todo.contains("<#include \"test-before-method.ftl\">"), todo);
        Assert.assertTrue(gen.contains("<#include \"test-call-site.ftl\">"), gen);
        Assert.assertTrue(todo.contains("<#include \"test-call-site.ftl\">"), todo);
        // requiredProperty / Secrets live once in the shared call-site include
        String call = Files.readString(CALL);
        Assert.assertTrue(call.contains("PropertyReader.getProperty"), call);
        Assert.assertTrue(call.contains("Secrets.property"), call);
        Assert.assertFalse(gen.contains("private static String requiredProperty"), gen);
        Assert.assertFalse(todo.contains("private static String requiredProperty"), todo);
    }
}
