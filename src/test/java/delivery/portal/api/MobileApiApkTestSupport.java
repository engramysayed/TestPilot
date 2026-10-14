package delivery.portal.api;

import delivery.mobile.apk.ApkAnalysisBackend;
import delivery.mobile.apk.ApkFixtures;
import delivery.mobile.apk.FixedApkAnalysisBackend;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared test double for portal mobile APK upload MVC tests (no real apkanalyzer).
 * Mode: READY (single launcher) or MULTI (ACTIVITY_SELECTION_REQUIRED).
 */
public final class MobileApiApkTestSupport {
    public enum Mode { READY, MULTI }

    public static final AtomicReference<Mode> MODE = new AtomicReference<>(Mode.READY);

    private MobileApiApkTestSupport() {
    }

    @TestConfiguration
    public static class ApkBackendConfig {
        @Bean
        @Primary
        public ApkAnalysisBackend mobileApiTestApkAnalysisBackend() {
            return path -> {
                Mode mode = MODE.get();
                if (mode == Mode.MULTI) {
                    return FixedApkAnalysisBackend.sample(
                            "com.example.multi", 2L, 26,
                            List.of("arm64-v8a"),
                            List.of("com.example.multi.MainActivity",
                                    "com.example.multi.LauncherAlias",
                                    "com.example.multi.SplashActivity"),
                            "cert_multi_aabb");
                }
                return FixedApkAnalysisBackend.sample(
                        "com.example.app", 10L, 26,
                        List.of("arm64-v8a"),
                        List.of("com.example.app.MainActivity"),
                        "cert_ready_deadbeef");
            };
        }
    }

    public static MockMultipartFile validApkMultipart(String filename) throws Exception {
        Path tmp = Files.createTempDirectory("m107-apk");
        Path apk = ApkFixtures.validMinimalApk(tmp, filename == null ? "demo.apk" : filename);
        byte[] bytes = Files.readAllBytes(apk);
        return new MockMultipartFile(
                "file",
                filename == null ? "demo.apk" : filename,
                "application/vnd.android.package-archive",
                bytes);
    }

    public static MockMultipartFile corruptApkMultipart(String filename) throws Exception {
        Path tmp = Files.createTempDirectory("m107-bad");
        Path apk = ApkFixtures.corruptNonZip(tmp, filename == null ? "bad.apk" : filename);
        byte[] bytes = Files.readAllBytes(apk);
        return new MockMultipartFile(
                "file",
                filename == null ? "bad.apk" : filename,
                "application/vnd.android.package-archive",
                bytes);
    }
}
