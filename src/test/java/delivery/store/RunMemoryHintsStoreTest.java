package delivery.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunMemoryHintsStoreTest {

    @Test
    public void loadForJob_readsApprovedItems() throws Exception {
        Path root = Path.of("./target/test-run-memory-hints");
        String jobId = "exec_test01";
        Path jobDir = root.resolve("execute-runs").resolve(jobId);
        Files.createDirectories(jobDir);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("items", List.of(Map.of(
                "id", "mem_x",
                "summary", "Login button",
                "locatorHint", "#login",
                "status", "approved")));
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(jobDir.resolve(RunMemoryHintsStore.JOB_FILE).toFile(), doc);

        List<String> lines = RunMemoryHintsStore.loadForJob(root, jobId);
        Assert.assertEquals(lines.size(), 1);
        Assert.assertTrue(lines.get(0).contains("#login"));
    }
}
