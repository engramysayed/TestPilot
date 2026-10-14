package delivery.mobile.apk;

import delivery.mobile.MobileProperties;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M1-02 acceptance: valid/corrupt APK, oversized, traversal/unsafe names, multiple launchers,
 * package mismatch, API/ABI/signature/downgrade rejection. No auto-uninstall fallback.
 */
public class ApkInspectorTest {
    private Path tmp;
    private MobileProperties props;

    @BeforeMethod
    public void setUp() throws Exception {
        tmp = Files.createTempDirectory("m102-apk");
        props = new MobileProperties();
        props.setUploadMaxBytes(1024 * 1024);
        props.setZipMaxUncompressedBytes(1024 * 1024);
        props.setZipMaxEntries(1000);
        props.setAllowDowngrade(false);
        props.setAllowSignatureReplacement(false);
    }

    @Test
    public void validApkInspectsAndResolvesSingleLauncher() throws Exception {
        Path apk = ApkFixtures.validMinimalApk(tmp, "ok.apk");
        ApkManifestInfo info = FixedApkAnalysisBackend.sample(
                "com.example.app", 10L, 26, List.of("arm64-v8a"),
                List.of("com.example.app.MainActivity"), "aabbccdd");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(info));
        ApkInspectionOutcome out = inspector.inspect(apk, "deadbeef", "blob/ref", new ApkInspectionRequest());
        Assert.assertEquals(out.getStatus(), ApkInspectionOutcome.Status.READY);
        Assert.assertEquals(out.getManifest().getPackageName(), "com.example.app");
        Assert.assertEquals(out.getLaunchActivity(), "com.example.app.MainActivity");
        Assert.assertEquals(out.getWaitActivity(), "com.example.app.MainActivity");
        Assert.assertFalse(inspector.allowsAutoUninstallFallback());
    }

    @Test
    public void corruptNonZipRejected() throws Exception {
        Path apk = ApkFixtures.corruptNonZip(tmp, "bad.apk");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(
                FixedApkAnalysisBackend.sample("x", 1, 21, List.of(), List.of("a"), null)));
        try {
            inspector.inspect(apk, "x", "ref", new ApkInspectionRequest());
            Assert.fail("expected APK_INVALID");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.APK_INVALID);
        }
    }

    @Test
    public void missingManifestRejected() throws Exception {
        Path apk = ApkFixtures.zipMissingManifest(tmp, "nom.apk");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(
                FixedApkAnalysisBackend.sample("x", 1, 21, List.of(), List.of("a"), null)));
        try {
            inspector.inspect(apk, "x", "ref", new ApkInspectionRequest());
            Assert.fail("expected APK_INVALID");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.APK_INVALID);
            Assert.assertTrue(e.getMessage().toLowerCase().contains("manifest"));
        }
    }

    @Test
    public void oversizedUploadRejectedAtQuarantine() throws Exception {
        props.setUploadMaxBytes(64);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", ApkFixtures.bytesOfSize(128));
        byte[] zip = ApkFixtures.zipBytes(entries);
        try {
            ApkUploadQuarantine.streamToQuarantine(
                    tmp, "t1", "p1", "big.apk", new ByteArrayInputStream(zip), props.getUploadMaxBytes());
            Assert.fail("expected OVERSIZED");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.OVERSIZED);
        }
    }

    @Test
    public void oversizedUncompressedArchiveRejected() throws Exception {
        props.setZipMaxUncompressedBytes(32);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", "tiny".getBytes());
        entries.put("payload.bin", ApkFixtures.bytesOfSize(64));
        Path apk = ApkFixtures.writeZipApk(tmp, "over.apk", entries);
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(
                FixedApkAnalysisBackend.sample("x", 1, 21, List.of(), List.of("a"), null)));
        try {
            inspector.inspect(apk, "x", "ref", new ApkInspectionRequest());
            Assert.fail("expected OVERSIZED");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.OVERSIZED);
        }
    }

    @Test
    public void traversalEntryRejected() throws Exception {
        Path apk = ApkFixtures.zipWithTraversal(tmp, "trav.apk");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(
                FixedApkAnalysisBackend.sample("x", 1, 21, List.of(), List.of("a"), null)));
        try {
            inspector.inspect(apk, "x", "ref", new ApkInspectionRequest());
            Assert.fail("expected UNSAFE_NAME");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.UNSAFE_NAME);
        }
    }

    @Test
    public void symlinkEntryRejected() throws Exception {
        Path apk = ApkFixtures.zipWithSymlink(tmp, "sym.apk");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(
                FixedApkAnalysisBackend.sample("x", 1, 21, List.of(), List.of("a"), null)));
        try {
            inspector.inspect(apk, "x", "ref", new ApkInspectionRequest());
            Assert.fail("expected UNSAFE_NAME");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.UNSAFE_NAME);
            Assert.assertTrue(e.getMessage().toLowerCase().contains("symlink"));
        }
    }

    @Test
    public void unsafeUploadFilenameAndRemoteUrlRejected() throws Exception {
        try {
            ApkUploadQuarantine.rejectRemoteUrl("https://evil.example/app.apk");
            Assert.fail();
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.REMOTE_SOURCE_REJECTED);
        }
        try {
            ApkUploadQuarantine.sanitizeFilename("../../etc/passwd.apk");
            Assert.fail();
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.UNSAFE_NAME);
        }
    }

    @Test
    public void multipleLaunchersRequireChoice() throws Exception {
        Path apk = ApkFixtures.validMinimalApk(tmp, "multi.apk");
        ApkManifestInfo info = FixedApkAnalysisBackend.sample(
                "com.example.app", 10L, 26, List.of("arm64-v8a"),
                List.of("com.example.app.LauncherA", "com.example.app.LauncherB"), "aabb");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(info));
        ApkInspectionOutcome out = inspector.inspect(apk, "hash", "ref", new ApkInspectionRequest());
        Assert.assertEquals(out.getStatus(), ApkInspectionOutcome.Status.ACTIVITY_SELECTION_REQUIRED);
        Assert.assertEquals(out.getLauncherCandidates().size(), 2);

        ApkInspectionRequest chosen = new ApkInspectionRequest();
        chosen.setSelectedLaunchActivity("com.example.app.LauncherB");
        chosen.setSelectedWaitActivity("com.example.app.WaitActivity");
        ApkInspectionOutcome ready = inspector.inspect(apk, "hash", "ref", chosen);
        Assert.assertEquals(ready.getStatus(), ApkInspectionOutcome.Status.READY);
        Assert.assertEquals(ready.getLaunchActivity(), "com.example.app.LauncherB");
        Assert.assertEquals(ready.getWaitActivity(), "com.example.app.WaitActivity");
    }

    @Test
    public void packageMismatchRejected() throws Exception {
        Path apk = ApkFixtures.validMinimalApk(tmp, "mm.apk");
        ApkManifestInfo info = FixedApkAnalysisBackend.sample(
                "com.other.app", 10L, 26, List.of("arm64-v8a"),
                List.of("com.other.app.Main"), "aabb");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(info));
        ApkInspectionRequest req = new ApkInspectionRequest();
        req.setExpectedPackage("com.example.app");
        try {
            inspector.inspect(apk, "h", "r", req);
            Assert.fail("expected APP_ID_MISMATCH");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.APP_ID_MISMATCH);
        }
    }

    @Test
    public void apiIncompatibleRejected() throws Exception {
        Path apk = ApkFixtures.validMinimalApk(tmp, "api.apk");
        ApkManifestInfo info = FixedApkAnalysisBackend.sample(
                "com.example.app", 10L, 30, List.of("arm64-v8a"),
                List.of("com.example.app.Main"), "aabb");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(info));
        ApkInspectionRequest req = new ApkInspectionRequest();
        req.setDeviceConstraints(new ApkRevisionPolicy.DeviceConstraints(26, "arm64-v8a"));
        try {
            inspector.inspect(apk, "h", "r", req);
            Assert.fail("expected API_INCOMPATIBLE");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.API_INCOMPATIBLE);
        }
    }

    @Test
    public void abiIncompatibleRejected() throws Exception {
        Path apk = ApkFixtures.validMinimalApk(tmp, "abi.apk");
        ApkManifestInfo info = FixedApkAnalysisBackend.sample(
                "com.example.app", 10L, 26, List.of("armeabi-v7a"),
                List.of("com.example.app.Main"), "aabb");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(info));
        ApkInspectionRequest req = new ApkInspectionRequest();
        req.setDeviceConstraints(new ApkRevisionPolicy.DeviceConstraints(30, "x86_64"));
        try {
            inspector.inspect(apk, "h", "r", req);
            Assert.fail("expected ABI_INCOMPATIBLE");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.ABI_INCOMPATIBLE);
        }
    }

    @Test
    public void downgradeRejectedUnlessApproved() throws Exception {
        Path apk = ApkFixtures.validMinimalApk(tmp, "down.apk");
        ApkManifestInfo info = FixedApkAnalysisBackend.sample(
                "com.example.app", 5L, 26, List.of("arm64-v8a"),
                List.of("com.example.app.Main"), "aabb");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(info));
        ApkInspectionRequest req = new ApkInspectionRequest();
        req.setPriorRevision(new ApkRevisionPolicy.PriorRevision("com.example.app", 10L, "aabb"));
        try {
            inspector.inspect(apk, "h", "r", req);
            Assert.fail("expected DOWNGRADE_REJECTED");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.DOWNGRADE_REJECTED);
        }
        req.setAllowDowngrade(true);
        ApkInspectionOutcome ok = inspector.inspect(apk, "h", "r", req);
        Assert.assertEquals(ok.getStatus(), ApkInspectionOutcome.Status.READY);
    }

    @Test
    public void signatureReplacementRejectedUnlessApproved() throws Exception {
        Path apk = ApkFixtures.validMinimalApk(tmp, "sig.apk");
        ApkManifestInfo info = FixedApkAnalysisBackend.sample(
                "com.example.app", 11L, 26, List.of("arm64-v8a"),
                List.of("com.example.app.Main"), "ffffffff");
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(info));
        ApkInspectionRequest req = new ApkInspectionRequest();
        req.setPriorRevision(new ApkRevisionPolicy.PriorRevision("com.example.app", 10L, "aabbccdd"));
        try {
            inspector.inspect(apk, "h", "r", req);
            Assert.fail("expected SIGNATURE_REPLACEMENT_REJECTED");
        } catch (ApkInspectionException e) {
            Assert.assertEquals(e.getCode(), ApkErrorCode.SIGNATURE_REPLACEMENT_REJECTED);
        }
        req.setAllowSignatureReplacement(true);
        Assert.assertEquals(inspector.inspect(apk, "h", "r", req).getStatus(),
                ApkInspectionOutcome.Status.READY);
    }

    @Test
    public void noAutoUninstallFallback() {
        Assert.assertFalse(ApkRevisionPolicy.autoUninstallFallbackAllowed());
        ApkInspector inspector = new ApkInspector(props, new FixedApkAnalysisBackend(
                FixedApkAnalysisBackend.sample("p", 1, 21, List.of(), List.of("a"), null)));
        Assert.assertFalse(inspector.allowsAutoUninstallFallback());
    }

    @Test
    public void quarantineComputesSha256AndSafePath() throws Exception {
        Path apk = ApkFixtures.validMinimalApk(tmp, "hash.apk");
        byte[] bytes = Files.readAllBytes(apk);
        var upload = ApkUploadQuarantine.streamToQuarantine(
                tmp.resolve("store"), "tenantA", "projB", "hash.apk",
                new ByteArrayInputStream(bytes), 1024 * 1024);
        Assert.assertTrue(upload.sha256Hex().matches("[0-9a-f]{64}"));
        Assert.assertTrue(Files.isRegularFile(upload.apkPath()));
        Assert.assertTrue(upload.apkPath().toString().contains("tenants/tenantA/projects/projB/mobile/quarantine"));
        Assert.assertEquals(upload.byteCount(), bytes.length);
    }

    @Test
    public void aapt2BadgingParserExtractsFields() throws Exception {
        String badging = """
                package: name='com.demo.app' versionCode='42' versionName='4.2' platformBuildVersionName='14'
                sdkVersion:'26'
                targetSdkVersion:'34'
                native-code: 'arm64-v8a' 'armeabi-v7a'
                launchable-activity: name='com.demo.app.MainActivity'  label='Demo' icon=''
                """;
        ApkManifestInfo info = ProcessApkAnalysisBackend.parseBadging(badging);
        Assert.assertEquals(info.getPackageName(), "com.demo.app");
        Assert.assertEquals(info.getVersionCode().longValue(), 42L);
        Assert.assertEquals(info.getVersionName(), "4.2");
        Assert.assertEquals(info.getMinSdk().intValue(), 26);
        Assert.assertEquals(info.getAbiList().size(), 2);
        Assert.assertEquals(info.getLauncherActivities().get(0), "com.demo.app.MainActivity");
    }
}
