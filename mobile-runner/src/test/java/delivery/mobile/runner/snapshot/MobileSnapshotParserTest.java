package delivery.mobile.runner.snapshot;

import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.testng.Assert.*;

public class MobileSnapshotParserTest {
    private final MobileSnapshotParser parser = new MobileSnapshotParser();

    private static String resource(String path) throws Exception {
        try (InputStream in = MobileSnapshotParserTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MobileSnapshotMeta meta() {
        return new MobileSnapshotMeta(1, 1_700_000_000_000L, "com.example.test", ".LoginActivity",
                "NATIVE", 0, 1080, 1920, "ar-EG", MobileSnapshotInvalidation.NONE);
    }

    @Test
    public void flattenedLoginParsesPasswordAndGeometry() throws Exception {
        MobileSnapshot snap = parser.parse(resource("mobile-snapshot/taxonomy/flattened-login.xml"), meta());
        assertTrue(snap.usable());
        assertTrue(snap.size() >= 4);
        MobileSnapshotNode password = snap.nodes().stream().filter(MobileSnapshotNode::password).findFirst().orElseThrow();
        assertTrue(password.password());
        assertEquals(password.text(), "", "password text must not surface via text()");
        assertFalse(password.rawText().isBlank());
        MobileSnapshotNode phone = snap.nodes().stream()
                .filter(n -> n.resourceId().endsWith("phone_Number_Input")).findFirst().orElseThrow();
        assertNotNull(phone.bounds());
        assertEquals(phone.bounds().width(), 920);
        assertTrue(phone.clickable());
    }

    @Test
    public void arabicLabelsPreserved() throws Exception {
        MobileSnapshot snap = parser.parse(resource("mobile-snapshot/taxonomy/arabic-labels.xml"), meta());
        assertTrue(snap.nodes().stream().anyMatch(n -> n.text().contains("تسجيل")));
        assertTrue(snap.nodes().stream().anyMatch(n -> n.contentDesc().contains("تسجيل")));
    }

    @Test
    public void flutterMergedCardNormalizesNewlines() throws Exception {
        MobileSnapshot snap = parser.parse(resource("mobile-snapshot/flutter/merged-card-newline.xml"), meta());
        MobileSnapshotNode card = snap.nodes().stream()
                .filter(n -> n.contentDesc().contains("Order")).findFirst().orElseThrow();
        assertTrue(card.contentDesc().contains("\n") || card.contentDesc().contains("Paid"));
        assertFalse(card.contentDescNormalized().contains("\n"));
        assertTrue(card.contentDescNormalized().contains("Order #1234"));
    }

    @Test
    public void flutterMergedPinKeypadDoesNotInventChildren() throws Exception {
        MobileSnapshot snap = parser.parse(resource("mobile-snapshot/flutter/merged-pin-keypad.xml"), meta());
        List<MobileSnapshotNode> pads = snap.nodes().stream()
                .filter(n -> n.contentDesc().contains("PIN")).toList();
        assertEquals(pads.size(), 1);
        assertTrue(pads.get(0).childIndexes().isEmpty());
    }

    @Test
    public void flutterSemanticsIdentifierSurfacesAsResourceId() throws Exception {
        MobileSnapshot snap = parser.parse(resource("mobile-snapshot/flutter/semantics-identifier.xml"), meta());
        assertTrue(snap.nodes().stream().anyMatch(n -> "phone_Number_Input".equals(n.resourceId())));
    }

    @Test
    public void multiWindowKeepsSystemUiAndImeAndDialog() throws Exception {
        MobileSnapshot snap = parser.parse(resource("mobile-snapshot/windows/ime-and-dialog.xml"), meta());
        assertTrue(snap.nodes().stream().anyMatch(MobileSnapshotNode::looksLikeSystemUi));
        assertTrue(snap.nodes().stream().anyMatch(n -> "dialog".equals(n.windowId())));
        assertTrue(snap.nodes().stream().anyMatch(n -> "ime".equals(n.windowId())));
        assertTrue(snap.nodes().stream().anyMatch(n -> n.resourceId().endsWith("perm_allow")));
    }

    @Test
    public void xxeAttemptRejected() throws Exception {
        String xml = resource("mobile-snapshot/security/xxe-attempt.xml");
        try {
            parser.parse(xml, meta());
            fail("expected XXE rejection");
        } catch (MobileSnapshotException e) {
            assertTrue(e.code() == MobileSnapshotException.Code.XXE_REJECTED
                            || e.code() == MobileSnapshotException.Code.MALFORMED,
                    e.code() + ": " + e.getMessage());
        }
    }

    @Test
    public void malformedRejected() throws Exception {
        try {
            parser.parse(resource("mobile-snapshot/security/malformed.xml"), meta());
            fail();
        } catch (MobileSnapshotException e) {
            assertEquals(e.code(), MobileSnapshotException.Code.MALFORMED);
        }
    }

    @Test
    public void oversizedBytesRejected() {
        MobileSnapshotLimits tiny = new MobileSnapshotLimits(64, 50_000, 64);
        MobileSnapshotParser p = new MobileSnapshotParser(tiny);
        String xml = "<hierarchy>" + "a".repeat(200) + "</hierarchy>";
        try {
            p.parse(xml, meta());
            fail();
        } catch (MobileSnapshotException e) {
            assertEquals(e.code(), MobileSnapshotException.Code.OVERSIZE_BYTES);
        }
    }

    @Test
    public void oversizedNodesRejected() {
        MobileSnapshotLimits tiny = new MobileSnapshotLimits(2_000_000, 5, 64);
        MobileSnapshotParser p = new MobileSnapshotParser(tiny);
        StringBuilder sb = new StringBuilder("<hierarchy>");
        for (int i = 0; i < 20; i++) {
            sb.append("<node class=\"android.view.View\" bounds=\"[0,0][1,1]\"/>");
        }
        sb.append("</hierarchy>");
        try {
            p.parse(sb.toString(), meta());
            fail();
        } catch (MobileSnapshotException e) {
            assertEquals(e.code(), MobileSnapshotException.Code.OVERSIZE_NODES);
        }
    }

    @Test
    public void hugeVirtualizedListFixtureParsesUnderDefaultLimits() {
        StringBuilder sb = new StringBuilder("<hierarchy><node class=\"android.widget.FrameLayout\" bounds=\"[0,0][1080,1920]\" package=\"com.example.test\">");
        for (int i = 0; i < 200; i++) {
            int top = i * 120;
            sb.append("<node class=\"android.widget.TextView\" text=\"Row ").append(i)
                    .append("\" bounds=\"[0,").append(top).append("][1080,").append(top + 100)
                    .append("]\" package=\"com.example.test\"/>");
        }
        sb.append("</node></hierarchy>");
        MobileSnapshot snap = parser.parse(sb.toString(), meta());
        assertTrue(snap.size() >= 200);
    }

    @Test
    public void invalidationClearsUsable() throws Exception {
        MobileSnapshot snap = parser.parse(resource("mobile-snapshot/taxonomy/bottom-tabs.xml"), meta());
        assertTrue(snap.usable());
        MobileSnapshot stale = snap.invalidate(MobileSnapshotInvalidation.SCROLL);
        assertFalse(stale.usable());
        assertEquals(stale.meta().invalidation(), MobileSnapshotInvalidation.SCROLL);
        assertEquals(stale.size(), snap.size());
    }

    @Test
    public void emptyRejected() {
        try {
            parser.parse("  ", meta());
            fail();
        } catch (MobileSnapshotException e) {
            assertEquals(e.code(), MobileSnapshotException.Code.EMPTY);
        }
    }

    @Test
    public void taxonomyFixturesParse() throws Exception {
        for (String path : List.of(
                "mobile-snapshot/taxonomy/permission-prompt.xml",
                "mobile-snapshot/taxonomy/debounced-search.xml",
                "mobile-snapshot/taxonomy/modal-flow.xml",
                "mobile-snapshot/taxonomy/async-states.xml",
                "mobile-snapshot/taxonomy/broken-accessibility.xml",
                "mobile-snapshot/taxonomy/dynamic-labels.xml"
        )) {
            MobileSnapshot snap = parser.parse(resource(path), meta());
            assertTrue(snap.size() >= 1, path);
            assertFalse(snap.sourceSha256().isBlank());
        }
    }
}
