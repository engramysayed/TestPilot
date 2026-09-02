package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class AdminDomainServiceTest {
    @Test
    public void listsAndDeletesDomainTree() throws Exception {
        Path root = Files.createTempDirectory("admin-domain");
        Path domain = root.resolve("throwaway-com");
        Path project = domain.resolve("prj_x");
        Files.createDirectories(project);
        Files.writeString(project.resolve("project.json"), "{\"version\":1}");
        Files.writeString(project.resolve("domain-locator-memory.json"),
                "{\"entries\":[{\"host\":\"t\",\"path\":\"/\",\"intentKey\":\"CLICK|x\"}]}");
        Files.writeString(project.resolve("locator-map.json"), "{\"pages\":[]}");
        Files.writeString(root.resolve("portal-db.mv.db"), "keep-me");

        AdminDomainService svc = new AdminDomainService(root);
        var list = svc.listDomains();
        Assert.assertEquals(list.size(), 1);
        Assert.assertEquals(list.get(0).get("domain"), "throwaway-com");
        Assert.assertEquals(list.get(0).get("projectCount"), 1);
        Assert.assertEquals(list.get(0).get("locatorMapCount"), 1);
        Assert.assertTrue((Boolean) list.get(0).get("locatorMapPresent"));

        Map<String, Object> detail = svc.getDomain("throwaway-com");
        Assert.assertEquals(detail.get("memoryEntries"), 1);

        try {
            svc.deleteDomain("throwaway-com", "wrong");
            Assert.fail("expected mismatch");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("confirm"));
        }
        Assert.assertTrue(Files.isDirectory(domain));

        Map<String, Object> deleted = svc.deleteDomain("throwaway-com", "throwaway-com");
        Assert.assertEquals(deleted.get("domain"), "throwaway-com");
        Assert.assertFalse(Files.exists(domain));
        Assert.assertTrue(Files.isRegularFile(root.resolve("portal-db.mv.db")));
    }

    @Test
    public void rejectsPathTraversal() throws Exception {
        Path root = Files.createTempDirectory("admin-domain-trav");
        AdminDomainService svc = new AdminDomainService(root);
        try {
            svc.deleteDomain("../etc", "../etc");
            Assert.fail("expected invalid");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
