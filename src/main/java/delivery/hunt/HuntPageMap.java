package delivery.hunt;

import delivery.authoring.DomCandidate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public record HuntPageMap(
        String url,
        String title,
        List<String> headings,
        List<String> alerts,
        List<DomCandidate> controls,
        List<String> dialogs
) {
    public static final int PROMPT_MAX_CHARS = 24_000;

    public int controlCount() {
        return controls.size();
    }

    public boolean isThin() {
        return controlCount() < HuntPageMapBuilder.THIN_CONTROL_THRESHOLD;
    }

    /**
     * Compact markdown for the planner prompt. Always keeps URL, title, headings, alerts, and
     * dialogs; drops lowest-priority controls (from the end) if the result exceeds
     * {@link #PROMPT_MAX_CHARS}.
     */
    public String toPromptMd() {
        int included = controls.size();
        while (included >= 0) {
            String md = renderPromptMd(controls.subList(0, included));
            if (md.length() <= PROMPT_MAX_CHARS) {
                return md;
            }
            included--;
        }
        return renderPromptMd(List.of());
    }

    private String renderPromptMd(List<DomCandidate> includedControls) {
        StringBuilder sb = new StringBuilder();
        sb.append("## URL\n\n").append(nullToEmpty(url)).append("\n\n");
        sb.append("## Title\n\n").append(nullToEmpty(title)).append("\n\n");

        sb.append("## Headings\n\n");
        appendBulletList(sb, headings);

        sb.append("## Alerts\n\n");
        appendBulletList(sb, alerts);

        sb.append("## Dialogs\n\n");
        appendBulletList(sb, dialogs);

        sb.append("## Controls\n\n");
        if (includedControls.isEmpty()) {
            sb.append("_none_\n");
        } else {
            for (DomCandidate c : includedControls) {
                sb.append("- ").append(c.strategy()).append(":").append(c.value());
                if (c.label() != null && !c.label().isBlank()) {
                    sb.append(" — ").append(c.label().trim());
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    public JSONObject toJsonObject() {
        JSONObject o = new JSONObject();
        o.put("url", nullToEmpty(url));
        o.put("title", nullToEmpty(title));
        o.put("headings", new JSONArray(headings));
        o.put("alerts", new JSONArray(alerts));

        JSONArray controlsArr = new JSONArray();
        for (DomCandidate c : controls) {
            JSONObject co = new JSONObject();
            co.put("id", c.id());
            co.put("strategy", c.strategy());
            co.put("value", c.value());
            co.put("tag", c.tag());
            co.put("label", c.label());
            controlsArr.put(co);
        }
        o.put("controls", controlsArr);
        o.put("dialogs", new JSONArray(dialogs));
        return o;
    }

    private static void appendBulletList(StringBuilder sb, List<String> items) {
        if (items == null || items.isEmpty()) {
            sb.append("_none_\n\n");
            return;
        }
        for (String item : items) {
            sb.append("- ").append(item).append("\n");
        }
        sb.append("\n");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
