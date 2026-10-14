package delivery.mobile;

import delivery.mobile.db.MobileSchemaMigration;
import delivery.mobile.model.MobileAppRevisionRecord;
import delivery.mobile.model.MobileDeviceRecord;
import delivery.mobile.model.ProjectPlatform;
import delivery.portal.PortalApplication;
import delivery.portal.model.ProjectRecord;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import delivery.portal.service.PortalStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.util.List;

/**
 * M1-01: platform backfill, migration reentry, platform immutability, tenant FK rejection,
 * old web project unchanged.
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m101;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-mobile-m101",
        "delivery.work-dir=./target/test-delivery-work-mobile-m101",
        "delivery.mobile.enabled=false",
        "delivery.mobile.live-tests=false"
})
public class MobileM101EntitiesTest extends AbstractTestNGSpringContextTests {
    @Autowired PortalStore portalStore;
    @Autowired MobilePortalStore mobileStore;
    @Autowired ProjectRepository projectRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MobileProperties mobileProperties;

    @Test
    public void webCreateBackfillsPlatformWebAndOldProjectUnchanged() {
        ProjectRecord web = portalStore.createProject("Legacy Web", "https://example.test/", 1L);
        Assert.assertEquals(web.getPlatform(), "WEB");
        ProjectEntity entity = projectRepository.findByProjectId(web.getProjectId()).orElseThrow();
        Assert.assertEquals(ProjectPlatform.parse(entity.getPlatform()), ProjectPlatform.WEB);
        // baseUrl and naming behaviour unchanged for web projects
        Assert.assertEquals(entity.getBaseUrl(), "https://example.test/");
        Assert.assertFalse(mobileProperties.isEnabled());
    }

    @Test
    public void androidCreateSetsPlatformAndIsImmutable() {
        ProjectRecord android = portalStore.createProject("Phone App", null, 1L, ProjectPlatform.ANDROID);
        Assert.assertEquals(android.getPlatform(), "ANDROID");
        portalStore.rejectPlatformMutation(android.getProjectId(), "ANDROID"); // same — ok
        try {
            portalStore.rejectPlatformMutation(android.getProjectId(), "WEB");
            Assert.fail("expected immutability rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("immutable"));
        }
        // persisted value unchanged
        Assert.assertEquals(
                projectRepository.findByProjectId(android.getProjectId()).orElseThrow().getPlatform(),
                "ANDROID");
    }

    @Test
    public void nullPlatformRowsBackfillToWebOnMigrationReentry() throws Exception {
        // Simulate a legacy row with NULL platform (column exists).
        jdbc.update(
                "INSERT INTO projects (project_id, name, owner_user_id, latest_version, platform) VALUES (?,?,?,?,NULL)",
                "prj_legacy_null", "NullPlat", 99L, 0);
        // Re-run migration: must be idempotent and backfill WEB
        MobileSchemaMigration.migrate(dataSource);
        MobileSchemaMigration.migrate(dataSource); // reentry
        String platform = jdbc.queryForObject(
                "SELECT platform FROM projects WHERE project_id = ?", String.class, "prj_legacy_null");
        Assert.assertEquals(platform, "WEB");
    }

    @Test
    public void migrationCreatesMobileTablesIdempotently() throws Exception {
        MobileSchemaMigration.migrate(dataSource);
        MobileSchemaMigration.migrate(dataSource);
        Integer apps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME)='MOBILE_APPS'", Integer.class);
        Assert.assertTrue(apps != null && apps >= 1);
        Integer leases = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME)='MOBILE_DEVICE_LEASES'", Integer.class);
        Assert.assertTrue(leases != null && leases >= 1);
    }

    @Test
    public void tenantForeignAppReferenceRejected() {
        ProjectRecord a = portalStore.createProject("A", null, 1L, ProjectPlatform.ANDROID);
        ProjectRecord b = portalStore.createProject("B", null, 2L, ProjectPlatform.ANDROID);
        // Force distinct tenants on entities (workspace bootstrap may collide in mem tests)
        ProjectEntity ea = projectRepository.findByProjectId(a.getProjectId()).orElseThrow();
        ProjectEntity eb = projectRepository.findByProjectId(b.getProjectId()).orElseThrow();
        ea.setTenantId("tenant_a");
        eb.setTenantId("tenant_b");
        projectRepository.save(ea);
        projectRepository.save(eb);

        MobileAppRevisionRecord draft = new MobileAppRevisionRecord();
        draft.setSource("APK");
        draft.setPackageName("com.example.a");
        draft.setLaunchActivity("com.example.a.Main");
        draft.setWaitActivity("com.example.a.Main");
        draft.setVerification("UPLOADED");
        draft.setAbiList(List.of("arm64-v8a"));
        MobileAppRevisionRecord saved = mobileStore.registerAppRevision("tenant_a", a.getProjectId(), draft);
        Assert.assertEquals(saved.getPackageName(), "com.example.a");
        Assert.assertEquals(saved.getSource(), "APK");

        try {
            mobileStore.registerAppRevision("tenant_b", a.getProjectId(), draft);
            Assert.fail("expected foreign tenant rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("foreign"));
        }

        try {
            mobileStore.createDeviceProfile("tenant_b", b.getProjectId(), "phone", null, saved.getAppRevisionId());
            Assert.fail("expected foreign app revision rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("foreign"));
        }
    }

    @Test
    public void deviceRegistrationRejectsCrossTenantSerialBinding() {
        MobileDeviceRecord d1 = mobileStore.registerDevice(
                "tenant_x", "host1", "fp-serial-1", "ser…1", "Pixel", "Pixel 7", 33, "arm64-v8a");
        Assert.assertEquals(d1.getBusy(), "AVAILABLE");
        Assert.assertEquals(d1.getReadiness(), "READY");
        Assert.assertEquals(d1.getAuthorizedState(), "AUTHORIZED");
        Assert.assertEquals(d1.getSerialShort(), "ser…1");
        try {
            mobileStore.registerDevice(
                    "tenant_y", "host1", "fp-serial-1", "ser…1", "Stolen", "Pixel 7", 33, "arm64-v8a");
            Assert.fail("expected cross-tenant binding rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("foreign"));
        }
        // same tenant re-register returns existing
        MobileDeviceRecord again = mobileStore.registerDevice(
                "tenant_x", "host1", "fp-serial-1", "ser…1", "Pixel", "Pixel 7", 33, "arm64-v8a");
        Assert.assertEquals(again.getDeviceId(), d1.getDeviceId());
    }

    @Test
    public void leaseRequiresSameTenantDevice() {
        MobileDeviceRecord device = mobileStore.registerDevice(
                "tenant_lease", "host2", "fp-lease", "L1", "Phone", "Model", 30, "armeabi-v7a");
        var lease = mobileStore.createLease("tenant_lease", device.getDeviceId(), "job_1", "att_1");
        Assert.assertEquals(lease.getTenantId(), "tenant_lease");
        Assert.assertEquals(lease.getState(), "RESERVED");
        try {
            mobileStore.createLease("other_tenant", device.getDeviceId(), "job_2", "att_2");
            Assert.fail("expected foreign lease rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("foreign"));
        }
    }
}
