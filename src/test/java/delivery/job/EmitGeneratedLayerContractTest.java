package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** P1-03: emit regenerates every IR case, then compiles, then publishes. */
public class EmitGeneratedLayerContractTest {

    @Test
    public void emitCodegensEveryDraftIncludingReusedThenCompilesBeforePublish() throws Exception {
        String source = Files.readString(Path.of("src/main/java/delivery/job/EmitPhase.java"));
        Assert.assertFalse(source.contains("if (d.status() != TcDraftStatus.REUSED)"),
                "UPDATE must not skip REUSED drafts at codegen");
        Assert.assertTrue(source.contains("CodeWriter"), source);
        int compileAt = source.indexOf("EmitCompileCheck.runIfEnabled");
        int publishAt = source.indexOf("store.saveVersion");
        Assert.assertTrue(compileAt >= 0 && publishAt > compileAt,
                "compile check must run before publishing a new version");
    }
}
