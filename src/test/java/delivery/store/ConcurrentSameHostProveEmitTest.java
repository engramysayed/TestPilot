package delivery.store;

import delivery.authoring.AuthoringEngine;
import delivery.authoring.PrecisionJobConfig;
import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.ConversionJobRunner;
import delivery.job.TenantScope;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Concurrent same-host isolation through ProvePhase, EmitPhase, and the published ZIP.
 * Controlled local pages only; no live LLM.
 */
public class ConcurrentSameHostProveEmitTest {

    private com.sun.net.httpserver.HttpServer server;
    private String baseUrl;

    @BeforeClass
    public void startLocalPage() throws Exception {
        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        System.setProperty("BROWSER_HEADLESS", "true");
        System.setProperty("delivery.vision.grounding.enabled", "false");
        byte[] home = """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8"><title>Checkout</title></head>
                <body>
                  <h1>Checkout</h1>
                  <p>Ready to continue.</p>
                  <a id="continue-checkout" data-test="continue-checkout" href="/confirmed.html">Continue</a>
                </body></html>
                """.getBytes(StandardCharsets.UTF_8);
        byte[] confirmed = """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8"><title>Confirmed</title></head>
                <body>
                  <h1>Order confirmed</h1>
                  <p>Thank you.</p>
                </body></html>
                """.getBytes(StandardCharsets.UTF_8);
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = "/confirmed.html".equals(path) ? confirmed : home;
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterClass(alwaysRun = true)
    public void stopLocalPage() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void concurrentProveEmitDownloadsKeepCustomerSentinelsApart() throws Exception {
        Path root = Files.createTempDirectory("prove-emit");
        Path work = root.resolve("work");
        Path store = root.resolve("store");
        TenantId alice = TenantId.mint();
        TenantId bob = TenantId.mint();
        Path aliceExcel = excel(root.resolve("alice.xlsx"), "ALICE_SENTINEL");
        Path bobExcel = excel(root.resolve("bob.xlsx"), "BOB_SENTINEL");
        ConversionJobRequest aliceReq = hosted(aliceExcel, "prj_alice", "job_alice01", alice, work, store);
        ConversionJobRequest bobReq = hosted(bobExcel, "prj_bob", "job_bob00001", bob, work, store);

        CyclicBarrier start = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Exception> error = new AtomicReference<>();
        AtomicReference<ConversionJobResult> aliceResult = new AtomicReference<>();
        AtomicReference<ConversionJobResult> bobResult = new AtomicReference<>();
        Thread t1 = new Thread(() -> runProveEmit(aliceReq, start, done, error, aliceResult));
        Thread t2 = new Thread(() -> runProveEmit(bobReq, start, done, error, bobResult));
        t1.start();
        t2.start();
        Assert.assertTrue(done.await(8, TimeUnit.MINUTES), "prove+emit timed out");
        Assert.assertNull(error.get(), error.get() == null ? "" : error.get().toString());

        Path aliceZip = new ProjectStore(store, baseUrl, alice).latestVersionZip("prj_alice").orElseThrow();
        Path bobZip = new ProjectStore(store, baseUrl, bob).latestVersionZip("prj_bob").orElseThrow();
        Assert.assertNotEquals(aliceZip, bobZip);
        Assert.assertTrue(Files.isRegularFile(aliceZip));
        Assert.assertTrue(Files.isRegularFile(bobZip));
        Assert.assertNotNull(aliceResult.get());
        Assert.assertNotNull(bobResult.get());
        Assert.assertTrue(Files.isRegularFile(aliceResult.get().zipFile()));
        Assert.assertTrue(Files.isRegularFile(bobResult.get().zipFile()));
        Assert.assertNotEquals(aliceResult.get().zipFile().normalize(), bobResult.get().zipFile().normalize());

        String aliceIr = irText(ScopePaths.jobWorkDir(work, alice, "job_alice01"));
        String bobIr = irText(ScopePaths.jobWorkDir(work, bob, "job_bob00001"));
        Assert.assertTrue(aliceIr.contains("ALICE_SENTINEL"), aliceIr);
        Assert.assertFalse(aliceIr.contains("BOB_SENTINEL"), aliceIr);
        Assert.assertTrue(bobIr.contains("BOB_SENTINEL"), bobIr);
        Assert.assertFalse(bobIr.contains("ALICE_SENTINEL"), bobIr);

        String alicePkg = zipText(aliceZip);
        String bobPkg = zipText(bobZip);
        Assert.assertTrue(alicePkg.contains("ALICE_SENTINEL"), "alice zip missing sentinel");
        Assert.assertFalse(alicePkg.contains("BOB_SENTINEL"), "alice zip leaked bob sentinel");
        Assert.assertTrue(bobPkg.contains("BOB_SENTINEL"), "bob zip missing sentinel");
        Assert.assertFalse(bobPkg.contains("ALICE_SENTINEL"), "bob zip leaked alice sentinel");
    }

    private ConversionJobRequest hosted(
            Path excel, String projectId, String jobId, TenantId tenant, Path work, Path store
    ) {
        return new ConversionJobRequest(
                projectId,
                excel,
                baseUrl,
                "",
                "",
                work,
                store,
                Path.of("customer-framework-template"),
                "NEW",
                "http://127.0.0.1:9",
                "unused",
                false,
                false,
                AuthoringEngine.KEEL,
                PrecisionJobConfig.DEFAULTS,
                tenant,
                jobId,
                TenantScope.HOSTED
        );
    }

    private static Path excel(Path path, String sentinel) throws Exception {
        ManualTcExcelWriter.write(path, List.of(new ManualTestCase(
                "TC_01",
                "Continue " + sentinel + " checkout",
                "",
                "1. Click Continue",
                "Page shows " + sentinel,
                "P1",
                "",
                "",
                sentinel,
                "AUTOMATE"
        )));
        return path;
    }

    private static void runProveEmit(
            ConversionJobRequest request,
            CyclicBarrier start,
            CountDownLatch done,
            AtomicReference<Exception> error,
            AtomicReference<ConversionJobResult> result
    ) {
        try {
            start.await(30, TimeUnit.SECONDS);
            result.set(new ConversionJobRunner().run(request));
        } catch (Exception e) {
            error.compareAndSet(null, e);
        } finally {
            done.countDown();
        }
    }

    private static String irText(Path jobWork) throws Exception {
        Path irDir = jobWork.resolve("ir");
        Assert.assertTrue(Files.isDirectory(irDir), "missing IR dir: " + irDir);
        StringBuilder out = new StringBuilder();
        try (var files = Files.list(irDir)) {
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json")) {
                    out.append(Files.readString(file));
                }
            }
        }
        Assert.assertFalse(out.isEmpty(), "no IR json under " + irDir);
        return out.toString();
    }

    private static String zipText(Path zip) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                out.write(('\n' + entry.getName() + '\n').getBytes(StandardCharsets.UTF_8));
                in.transferTo(out);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
