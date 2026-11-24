package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Copies the Android customer template, writes screens from IR, compiles them, then zips. */
public final class MobileAutomatePack {

    private MobileAutomatePack() {}

    public static Path zip(Path templateRoot, Path workDir, List<MobileCaseIr> cases) throws Exception {
        if (templateRoot == null || !Files.isDirectory(templateRoot)) {
            throw new IllegalStateException("Android framework template is missing: " + templateRoot);
        }
        Path project = workDir.resolve("project");
        Path zip = workDir.resolve("package.zip");
        MobileFrameworkPackager packager = new MobileFrameworkPackager();
        packager.copyTemplate(templateRoot, project);
        new MobileCodeWriter().writeVerified(project, cases, NamingMap.empty());
        int generated = cases == null ? 0 : cases.size();
        packager.writeCoverageReport(project, generated, 0);
        MobileCompileGate.Result gate = MobileCompileGate.compileTests(project);
        if (!gate.ok()) {
            throw new IllegalStateException(
                    "The generated Android project did not compile. " + gate.message());
        }
        packager.zip(project, zip);
        return zip;
    }
}
