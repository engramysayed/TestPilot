package delivery.mobile.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.identity.TenantId;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MobileAuthProfilePortalStoreTest {
    private static final String TENANT = TenantId.mint().value();
    private static final String PROJECT = "proj_m306_1";

    private Path root;
    private MobileAuthProfilePortalStore store;

    @BeforeMethod
    public void setUp() throws Exception {
        root = Files.createTempDirectory("m306-auth-store");
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(root.toString());
        store = new MobileAuthProfilePortalStore(props, new ObjectMapper());
    }

    private AuthProfileRevision rev(String profileId, String revisionId, long ts) {
        return new AuthProfileRevision(
                profileId, revisionId, TENANT, PROJECT,
                "TC-LOGIN", "", AuthMode.ENSURE_LOGGED_IN,
                List.of(), List.of(), "", "",
                new LogicalAccountRef("a1", "A"),
                List.of(SecretInputDeclaration.otp("auth.otp")),
                List.of(), ts);
    }

    @Test
    public void createEditConflictAndSanitize() throws Exception {
        AuthProfileRevision r1 = store.create(TENANT, PROJECT, rev("p1", "r1", 10));
        Assert.assertEquals(store.activeRevisionId(TENANT, PROJECT, "p1"), "r1");
        AuthProfileRevision r2 = store.publishEdit(TENANT, PROJECT, "r1", rev("p1", "r2", 20));
        Assert.assertEquals(r2.revisionId(), "r2");
        Assert.assertEquals(store.activeRevisionId(TENANT, PROJECT, "p1"), "r2");
        try {
            store.publishEdit(TENANT, PROJECT, "r1", rev("p1", "r3", 30));
            Assert.fail("expected conflict");
        } catch (AuthContractException e) {
            Assert.assertEquals(e.code(), "REVISION_CONFLICT");
        }
        String json = Files.readString(store.storePath(TENANT, PROJECT));
        Assert.assertFalse(json.contains("SECRET_CANARY"));
        Assert.assertTrue(store.getRevision(TENANT, PROJECT, "p1", "r1").isPresent());
        Assert.assertEquals(r1.projectId(), PROJECT);
    }
}
