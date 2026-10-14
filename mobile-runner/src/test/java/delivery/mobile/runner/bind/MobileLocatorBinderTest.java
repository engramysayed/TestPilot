package delivery.mobile.runner.bind;

import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotInvalidation;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotParser;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.*;

public class MobileLocatorBinderTest {
    private final MobileSnapshotParser parser = new MobileSnapshotParser();
    private final MobileLocatorBinder binder = new MobileLocatorBinder();

    private static MobileSnapshotMeta meta() {
        return new MobileSnapshotMeta(1, 1_700_000_000_000L, "com.example.test", ".Main",
                "NATIVE", 0, 1080, 1920, "en", MobileSnapshotInvalidation.NONE);
    }

    private MobileSnapshot snap(String path) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return parser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), meta());
        }
    }

    @Test
    public void userRankWinsOverStrongerAutomatic() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/taxonomy/flattened-login.xml");
        // USER picks content-desc even though resource-id exists
        LocatorRequest user = LocatorRequest.user(LocatorStrategy.ACCESSIBILITY_ID, "Phone number", ActionKind.TYPE);
        BindResult r = binder.bind(s, user);
        assertEquals(r.status(), BindStatus.BOUND);
        assertEquals(r.selected().orElseThrow().origin(), LocatorOrigin.USER);
        assertEquals(r.selected().orElseThrow().tier(), RankTier.USER_OVERRIDE);
        assertEquals(r.selected().orElseThrow().selectorValue(), "Phone number");
    }

    @Test
    public void badUserLocatorRecordsFallback() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/taxonomy/flattened-login.xml");
        LocatorRequest bad = LocatorRequest.user(LocatorStrategy.RESOURCE_ID, "com.example.test:id/nope", ActionKind.TAP);
        BindResult r = binder.bind(s, bad, true);
        assertTrue(r.userFallback());
        // hierarchy may not find "nope" either → USER_LOCATOR_REJECTED
        assertEquals(r.status(), BindStatus.USER_LOCATOR_REJECTED);

        // bad user text, but hierarchy resource-id proposal for Sign in button via propose
        LocatorRequest badText = LocatorRequest.user(LocatorStrategy.TEXT, "Not A Real Label", ActionKind.TAP);
        BindResult r2 = binder.bind(s, badText, true);
        assertTrue(r2.userFallback());
        assertTrue(r2.status() == BindStatus.USER_LOCATOR_REJECTED || r2.status() == BindStatus.NOT_FOUND
                || r2.isBound());
    }

    @Test
    public void badUserFallsBackToHierarchyResourceId() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/taxonomy/flattened-login.xml");
        // USER wrong strategy value that won't match; preferHierarchyFallback proposes by same value string
        // Use a value that hierarchy can resolve via resource-id tail when user used TEXT wrongly
        LocatorRequest bad = new LocatorRequest(
                LocatorStrategy.TEXT, "com.example.test:id/sign_In_Button",
                LocatorOrigin.USER, ActionKind.TAP, "NATIVE", 1);
        BindResult r = binder.bind(s, bad, true);
        assertTrue(r.userFallback());
        assertTrue(r.isBound(), r.status() + " " + r.detail());
        assertEquals(r.selected().orElseThrow().strategy(), LocatorStrategy.RESOURCE_ID);
    }

    @Test
    public void exactSelectorBytesPreserved() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/taxonomy/arabic-labels.xml");
        String arabic = null;
        // find arabic text from snapshot
        var node = s.nodes().stream().filter(n -> !n.text().isEmpty() && n.text().chars().anyMatch(c -> c > 127))
                .findFirst().orElseThrow();
        arabic = node.text();
        LocatorRequest user = LocatorRequest.user(LocatorStrategy.TEXT, arabic, ActionKind.TAP);
        BindResult r = binder.bind(s, user);
        assertTrue(r.isBound(), r.detail());
        assertEquals(r.selected().orElseThrow().selectorValue(), arabic);
    }

    @Test
    public void sameNodeDedup() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/taxonomy/flattened-login.xml");
        BindResult r = binder.proposeHierarchy(s, "Phone number", ActionKind.TYPE);
        long phoneNodes = r.ranked().stream()
                .filter(c -> s.node(c.nodeIndex()).resourceId().endsWith("phone_Number_Input"))
                .map(Candidate::nodeIndex).distinct().count();
        assertEquals(phoneNodes, 1L, "same live node must appear once after dedup");
    }

    @Test
    public void identicalDistinctControlsRemainAmbiguous() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/bind/duplicate-labels.xml");
        LocatorRequest req = LocatorRequest.hierarchy(LocatorStrategy.TEXT, "Delete", ActionKind.TAP);
        BindResult r = binder.bind(s, req);
        assertEquals(r.status(), BindStatus.AMBIGUOUS);
        assertTrue(r.ranked().size() >= 2);
    }

    @Test
    public void invalidNativeCssRejected() {
        MobileSnapshot s = parser.parse(
                "<hierarchy><node class=\"android.view.View\" bounds=\"[0,0][1,1]\"/></hierarchy>", meta());
        LocatorRequest css = LocatorRequest.user(LocatorStrategy.CSS, ".btn", ActionKind.TAP);
        BindResult r = binder.bind(s, css);
        assertEquals(r.status(), BindStatus.INVALID_STRATEGY);
        assertTrue(r.detail().toLowerCase().contains("css"));
    }

    @Test
    public void noInventedFlutterChildren() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/flutter/merged-pin-keypad.xml");
        BindResult r = binder.proposeHierarchy(s, "PIN keypad", ActionKind.TAP);
        assertTrue(r.isBound(), r.detail());
        long pinLike = r.ranked().stream()
                .filter(c -> s.node(c.nodeIndex()).contentDesc().contains("PIN"))
                .map(Candidate::nodeIndex).distinct().count();
        assertEquals(pinLike, 1L);
        assertTrue(s.node(r.selected().orElseThrow().nodeIndex()).childIndexes().isEmpty());
        // snapshot itself has no digit children invented under the merged keypad
        assertEquals(s.nodes().stream().filter(n -> n.contentDesc().contains("PIN")).count(), 1L);
    }

    @Test
    public void innermostMatchThenClickableFirst() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/bind/innermost-nested.xml");
        LocatorRequest req = LocatorRequest.hierarchy(LocatorStrategy.ACCESSIBILITY_ID, "Sign in", ActionKind.TAP);
        BindResult r = binder.bind(s, req);
        assertTrue(r.isBound(), r.detail());
        assertTrue(s.node(r.selected().orElseThrow().nodeIndex()).clickable());
        assertTrue(s.node(r.selected().orElseThrow().nodeIndex()).className().contains("Button"));
    }

    @Test
    public void labelSiblingRankedBelowResourceId() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/bind/label-sibling-form.xml");
        BindResult byLabel = binder.proposeHierarchy(s, "Phone number", ActionKind.TYPE);
        assertTrue(byLabel.isBound(), byLabel.detail());
        assertEquals(byLabel.selected().orElseThrow().strategy(), LocatorStrategy.LABEL_SIBLING);
        assertEquals(byLabel.selected().orElseThrow().tier(), RankTier.LABEL_SIBLING);

        // When resource-id also matches same semantic, resource-id wins
        MobileSnapshot login = snap("mobile-snapshot/taxonomy/flattened-login.xml");
        BindResult mixed = binder.proposeHierarchy(login, "com.example.test:id/phone_Number_Input", ActionKind.TYPE);
        assertTrue(mixed.isBound());
        assertEquals(mixed.selected().orElseThrow().tier(), RankTier.RESOURCE_ID);
        assertTrue(RankTier.LABEL_SIBLING.ordinal() > RankTier.RESOURCE_ID.ordinal());
    }

    @Test
    public void newlineNormalizedMatch() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/flutter/merged-card-newline.xml");
        String want = MatchNormalizer.normalize(
                s.nodes().stream().filter(n -> n.contentDesc().contains("Order"))
                        .findFirst().orElseThrow().contentDesc());
        LocatorRequest req = LocatorRequest.hierarchy(LocatorStrategy.ACCESSIBILITY_ID, want, ActionKind.TAP);
        BindResult r = binder.bind(s, req);
        assertTrue(r.isBound(), r.status() + " " + r.detail());
        String observed = s.node(r.selected().orElseThrow().nodeIndex()).contentDesc();
        assertEquals(MatchNormalizer.normalize(observed), want);
        assertTrue(MatchNormalizer.equalsNormalized(r.selected().orElseThrow().selectorValue(), want));
        // Normalized query matched; selector value preserves whatever bytes the DOM exposed
        assertFalse(want.contains("Order") && r.selected().orElseThrow().selectorValue().isBlank());
    }

    @Test
    public void hintNotValue() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/bind/hint-empty-field.xml");
        var node = s.nodes().stream().filter(n -> n.resourceId().endsWith("email_Input")).findFirst().orElseThrow();
        assertEquals(node.text(), "");
        assertTrue(CandidateGenerator.matchesHintNotValue(node, "Email address"));
        // TEXT strategy must NOT treat hint as text value
        LocatorRequest asText = LocatorRequest.hierarchy(LocatorStrategy.TEXT, "Email address", ActionKind.TYPE);
        BindResult r = binder.bind(s, asText);
        assertFalse(r.isBound(), "hint must not satisfy TEXT value match");
    }

    @Test
    public void systemBarAndImeExcludedFromCandidates() throws Exception {
        MobileSnapshot s = snap("mobile-snapshot/windows/ime-and-dialog.xml");
        var pool = CandidateGenerator.pool(s);
        assertTrue(pool.stream().noneMatch(n -> NodeEligibility.isIme(n)));
        assertTrue(pool.stream().noneMatch(n -> NodeEligibility.isSystemBar(n, 1080, 1920)));
        // dialog allow button still present
        assertTrue(pool.stream().anyMatch(n -> n.resourceId().endsWith("perm_allow")
                || "dialog".equals(n.windowId())
                || n.text().toLowerCase().contains("allow")));
    }

    @Test
    public void statusBarChangeLeavesBindingUnchanged() throws Exception {
        MobileSnapshot a = snap("mobile-snapshot/bind/status-bar-a.xml");
        MobileSnapshot b = snap("mobile-snapshot/bind/status-bar-b.xml");
        assertEquals(BindingFingerprint.of(a), BindingFingerprint.of(b));

        LocatorRequest req = LocatorRequest.hierarchy(LocatorStrategy.RESOURCE_ID,
                "com.example.test:id/sign_In_Button", ActionKind.TAP);
        BindResult ra = binder.bind(a, req);
        BindResult rb = binder.bind(b, req);
        assertTrue(ra.isBound() && rb.isBound());
        assertEquals(ra.selected().orElseThrow().selectorValue(), rb.selected().orElseThrow().selectorValue());
        assertEquals(ra.selected().orElseThrow().strategy(), rb.selected().orElseThrow().strategy());
    }
}
