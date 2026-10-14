package delivery.snapshot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.*;

/** Golden parity vectors (src/test/resources/keel/contract-v2-vectors.json); Codegen runs the same file. */
public class ContractV2VectorsTest {
    static JSONObject vectors() throws Exception {
        try (InputStream in = ContractV2VectorsTest.class.getResourceAsStream("/keel/contract-v2-vectors.json")) {
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test public void parseFormatRoundTrip() throws Exception {
        JSONArray a = vectors().getJSONArray("parse");
        for (int i = 0; i < a.length(); i++) {
            JSONObject v = a.getJSONObject(i);
            KeelTarget t = KeelTarget.parse(v.getString("in"));
            JSONArray hops = v.getJSONArray("hops");
            assertEquals(t.hops().size(), hops.length(), v.toString());
            for (int h = 0; h < hops.length(); h++) {
                assertEquals(t.hops().get(h).prefix(), hops.getJSONArray(h).getString(0) + "=", v.toString());
                assertEquals(t.hops().get(h).value(), hops.getJSONArray(h).getString(1), v.toString());
            }
            assertEquals(t.requiredVersion(), v.getInt("version"), v.toString());
            assertEquals(t.format(), v.getString("format"));
            assertEquals(KeelTarget.parse(t.format()), t);
        }
    }

    @Test public void invalidTargetsRejected() throws Exception {
        JSONArray a = vectors().getJSONArray("invalid");
        for (int i = 0; i < a.length(); i++) {
            String s = a.getString(i);
            expectThrows(IllegalArgumentException.class, () -> KeelTarget.parse(s));
        }
    }

    @Test public void pointScaling() throws Exception {
        JSONArray a = vectors().getJSONArray("point");
        for (int i = 0; i < a.length(); i++) {
            JSONObject v = a.getJSONObject(i);
            double[] d = KeelTarget.Point.parse(v.getString("point"))
                    .offsetFromCentre(v.getJSONArray("current").getDouble(0), v.getJSONArray("current").getDouble(1));
            assertEquals(d[0], v.getJSONArray("offset").getDouble(0), 1e-9, v.toString());
            assertEquals(d[1], v.getJSONArray("offset").getDouble(1), 1e-9, v.toString());
        }
    }

    @Test public void sanitizer() throws Exception {
        JSONArray a = vectors().getJSONArray("sanitizer");
        for (int i = 0; i < a.length(); i++) {
            JSONObject v = a.getJSONObject(i);
            assertEquals(TextLocatorSanitizer.allowed(v.getString("text")), v.getBoolean("allowed"), v.toString());
        }
    }

    @Test public void v1TargetsAndIrStayV1() {
        KeelTarget t = KeelTarget.parse("css=#a");
        assertEquals(t.requiredVersion(), 1);
        RankedLocators r = RankedLocators.of(java.util.List.of(ScoredLocator.keel(t, 0.6, "css-id")), "");
        assertFalse(r.toIrJson().has("contract"));
        RankedLocators r2 = RankedLocators.of(java.util.List.of(
                ScoredLocator.keel(KeelTarget.parse("text=\"Refresh\""), 0.65, "text")), "");
        assertEquals(r2.toIrJson().getInt("contract"), 2);
        assertEquals(KeelTarget.CONTRACT_VERSION, 2);
    }

    @Test public void maxVersionDefaultsToOne() {
        System.clearProperty(LocatorRanker.MAX_VERSION_KEY);
        assertEquals(LocatorRanker.maxContractVersion(), 1);
    }
}
