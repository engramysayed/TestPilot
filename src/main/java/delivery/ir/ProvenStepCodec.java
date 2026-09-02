package delivery.ir;

import delivery.codegen.ProvenStep;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ProvenStepCodec {
    private ProvenStepCodec() {
    }

    public static JSONObject toJson(ProvenStep s) {
        JSONObject o = new JSONObject();
        o.put("tcId", nullToEmpty(s.tcId()));
        o.put("pageName", nullToEmpty(s.pageName()));
        o.put("actionType", nullToEmpty(s.actionType()));
        o.put("action", nullToEmpty(s.action()));
        o.put("locatorStrategy", nullToEmpty(s.locatorStrategy()));
        o.put("locatorValue", nullToEmpty(s.locatorValue()));
        o.put("value", nullToEmpty(s.value()));
        o.put("assertionType", nullToEmpty(s.assertionType()));
        o.put("assertionExpected", nullToEmpty(s.assertionExpected()));
        o.put("validated", s.validated());
        o.put("rationale", nullToEmpty(s.rationale()));
        o.put("screenshotRelPath", nullToEmpty(s.screenshotRelPath()));
        return o;
    }

    public static ProvenStep fromJson(JSONObject o) {
        return new ProvenStep(
                o.optString("tcId", ""),
                o.optString("pageName", "Page"),
                o.optString("actionType", "elementAction"),
                o.optString("action", ""),
                o.optString("locatorStrategy", ""),
                o.optString("locatorValue", ""),
                o.optString("value", ""),
                o.optString("assertionType", ""),
                o.optString("assertionExpected", ""),
                o.optBoolean("validated", false),
                o.optString("rationale", ""),
                o.optString("screenshotRelPath", "")
        );
    }

    public static JSONArray toJsonArray(List<ProvenStep> steps) {
        JSONArray arr = new JSONArray();
        if (steps == null) {
            return arr;
        }
        for (ProvenStep s : steps) {
            arr.put(toJson(s));
        }
        return arr;
    }

    public static List<ProvenStep> fromJsonArray(JSONArray arr) {
        List<ProvenStep> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            out.add(fromJson(arr.getJSONObject(i)));
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
