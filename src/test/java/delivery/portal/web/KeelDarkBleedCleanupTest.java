package delivery.portal.web;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pack 11 — cheap static asserts for dark-mode white-bleed kill + FE cleanup.
 * No Spring context; reads tip CSS/HTML from module working directory.
 */
public class KeelDarkBleedCleanupTest {

    private static String read(String rel) throws Exception {
        Path p = Path.of(rel);
        if (!Files.exists(p)) {
            p = Path.of("keel-portal").resolve(rel);
        }
        Assert.assertTrue(Files.exists(p), "missing " + rel);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    public void calmCss_darkOverrides_killPaperWhiteTableHeaders() throws Exception {
        String calm = read("src/main/resources/static/css/keel-theme-calm.css");
        Assert.assertTrue(calm.contains("--table-head: #232933"), "dark table-head token");
        Assert.assertTrue(calm.contains("html[data-theme=\"dark\"] .table--runs th"), "runs th dark override");
        Assert.assertTrue(calm.contains("html[data-theme=\"dark\"] .keel-page .table th"), "page table th dark override");
        Assert.assertTrue(calm.contains("html[data-theme=\"dark\"] .keel-modal"), "modal dark surface");
        Assert.assertTrue(calm.contains("scrollbar-width: none"), "sidebar scrollbar hidden");
        Assert.assertTrue(calm.contains("--calm-accent: #087f78"), "quiet teal light kept");
        Assert.assertTrue(calm.contains("--calm-accent: #7ecfc5"), "quiet teal dark kept");
    }

    @Test
    public void adminUsers_rejectOnlyOnPending_notActive() throws Exception {
        String html = read("src/main/resources/templates/admin-users.html");
        Assert.assertTrue(html.contains("Pack 11: Reject only on pending"), "reject gate comment");
        int pending = html.indexOf("/* Pack 11: Reject only on pending");
        int approved = html.indexOf("if (st === 'APPROVED')", pending);
        Assert.assertTrue(pending >= 0 && approved > pending);
        String pendingChunk = html.substring(pending, approved);
        Assert.assertTrue(pendingChunk.contains("data-reject-user"), "Reject under PENDING");
        String approvedChunk = html.substring(approved, approved + 220);
        Assert.assertFalse(approvedChunk.contains("data-reject-user"), "no Reject on Active");
        Assert.assertTrue(html.contains("keelConfirm"), "confirm modal helper used");
    }

    @Test
    public void projectSettings_hideMembersEnvWebhook_andTaglineGone() throws Exception {
        String detail = read("src/main/resources/templates/project-detail.html");
        Assert.assertTrue(detail.contains("id=\"workspace-collab-panel\" hidden"));
        Assert.assertTrue(detail.contains("id=\"env-profiles-panel\" hidden"));
        Assert.assertTrue(detail.contains("id=\"webhook-panel\" hidden"));
        String projects = read("src/main/resources/templates/projects.html");
        Assert.assertFalse(projects.contains("projectcard-tagline"), "tagline creation removed");
        String confirm = read("src/main/resources/static/js/keel-confirm.js");
        Assert.assertTrue(confirm.contains("keelConfirm") && confirm.contains("keelAlert"));
        String frag = read("src/main/resources/templates/fragments.html");
        Assert.assertTrue(frag.contains("keel-confirm.js"));
    }

    @Test
    public void reskin_tableHead_usesTokens_notBarePaper() throws Exception {
        String reskin = read("src/main/resources/static/css/keel-reskin.css");
        Assert.assertTrue(reskin.contains("var(--table-head"), "table head tokenized");
        Assert.assertFalse(reskin.matches("(?s).*background:\\s*#fafbfc\\s*;.*"),
                "no bare #fafbfc background left in reskin");
        String roles = read("src/main/resources/static/css/keel-sidebar-roles.css");
        Assert.assertTrue(roles.contains("minmax(12rem,1fr)"), "perm grid tighter");
        Assert.assertTrue(roles.contains("keel-user-actions button") && roles.contains("font-size:.72rem"));
    }
}
