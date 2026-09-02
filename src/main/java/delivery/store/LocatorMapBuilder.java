package delivery.store;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Builds locator-map.json from Phase-1 IR for UPDATE reuse and human review. */
public final class LocatorMapBuilder {
    private LocatorMapBuilder() {
    }

    public static JSONObject build(List<TcDraft> drafts) {
        JSONObject root = new JSONObject();
        JSONObject pages = new JSONObject();
        JSONObject tcs = new JSONObject();
        if (drafts == null) {
            root.put("pages", pages);
            root.put("tcs", tcs);
            return root;
        }
        for (TcDraft d : drafts) {
            JSONObject tc = new JSONObject();
            tc.put("status", d.status().name());
            tc.put("title", d.title());
            tc.put("failureReason", d.failureReason());
            tc.put("blockerStepIndex", d.blockerStepIndex());
            tc.put("blockerIntent", d.blockerIntent());
            tc.put("lastPageUrl", d.lastPageUrl());
            JSONArray stepSummaries = new JSONArray();
            for (ProvenStep s : d.provenSteps()) {
                stepSummaries.put(stepEntry(s));
                mergeLocator(pages, s);
            }
            for (ProvenStep s : d.loginSteps()) {
                mergeLocator(pages, s);
            }
            tc.put("provenSteps", stepSummaries);
            tcs.put(d.tcId(), tc);
        }
        root.put("pages", pages);
        root.put("tcs", tcs);
        long passed = drafts.stream().filter(d -> d.status() == TcDraftStatus.PASSED
                || d.status() == TcDraftStatus.REUSED).count();
        long partial = drafts.stream().filter(d -> d.status() == TcDraftStatus.PARTIAL).count();
        long todo = drafts.stream().filter(d -> d.status() == TcDraftStatus.TODO).count();
        root.put("summary", new JSONObject()
                .put("passedOrReused", passed)
                .put("partial", partial)
                .put("todo", todo)
                .put("pageCount", pages.length()));
        return root;
    }

    private static JSONObject stepEntry(ProvenStep s) {
        JSONObject o = new JSONObject();
        o.put("pageName", s.pageName());
        o.put("action", s.action());
        o.put("strategy", s.locatorStrategy());
        o.put("value", s.locatorValue());
        o.put("typed", s.value());
        o.put("rationale", s.rationale());
        return o;
    }

    private static void mergeLocator(JSONObject pages, ProvenStep s) {
        if (s.locatorValue() == null || s.locatorValue().isBlank()) {
            return;
        }
        String page = s.pageName() == null || s.pageName().isBlank() ? "Page" : s.pageName();
        JSONObject pageObj = pages.optJSONObject(page);
        if (pageObj == null) {
            pageObj = new JSONObject();
            pages.put(page, pageObj);
        }
        String key = s.action() + ":" + s.locatorStrategy() + ":" + s.locatorValue();
        if (!pageObj.has(key)) {
            JSONObject loc = new JSONObject();
            loc.put("action", s.action());
            loc.put("strategy", s.locatorStrategy());
            loc.put("locator", s.locatorValue());
            loc.put("sampleValue", s.value());
            pageObj.put(key, loc);
        }
    }
}
