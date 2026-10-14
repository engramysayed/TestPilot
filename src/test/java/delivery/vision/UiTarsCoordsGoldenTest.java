package delivery.vision;

import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.*;

/** Golden UI-TARS coordinate vectors: screenshot size × DPR × padding × model space → CSS point. */
public class UiTarsCoordsGoldenTest {

    @AfterMethod public void clear() {
        System.clearProperty(UiTarsCoords.SPACE_KEY);
    }

    static JSONArray cases() throws Exception {
        try (InputStream in = UiTarsCoordsGoldenTest.class.getResourceAsStream("/vision/uitars-coords-golden.json")) {
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getJSONArray("cases");
        }
    }

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test public void exactChainMatchesGolden() throws Exception {
        JSONArray cs = cases();
        for (int i = 0; i < cs.length(); i++) {
            JSONObject c = cs.getJSONObject(i);
            int[] shot = ints(c.getJSONArray("screenshot")), inner = ints(c.getJSONArray("inner"));
            int[] send = ints(c.getJSONArray("send")), model = ints(c.getJSONArray("model"));
            UiTarsCoords.Space space = UiTarsCoords.Space.valueOf(c.getString("space"));
            if (!c.isNull("smartResize")) {
                assertEquals(UiTarsCoords.smartResizeV15(model[0], model[1]), ints(c.getJSONArray("smartResize")), c.toString(0));
            }
            UiTarsCoords.Grid g = new UiTarsCoords.Grid(space, model[0], model[1], send[0], send[1]);
            JSONArray pts = c.getJSONArray("points");
            for (int p = 0; p < pts.length(); p++) {
                JSONObject pt = pts.getJSONObject(p);
                double[] css = UiTarsCoords.toCss(pt.getJSONArray("model").getDouble(0), pt.getJSONArray("model").getDouble(1),
                        g, shot[0], shot[1], inner[0], inner[1]);
                assertEquals(css[0], pt.getJSONArray("css").getDouble(0), 1e-6, c.optString("space") + " " + pt);
                assertEquals(css[1], pt.getJSONArray("css").getDouble(1), 1e-6, c.optString("space") + " " + pt);
            }
        }
    }

    /** Real pipeline: image prep (resize + pad) → parseUiTars → toOriginal → CoordinateMapper (int boxes). */
    @Test public void productionPipelineWithinOneAndAHalfCssPx() throws Exception {
        JSONArray cs = cases();
        for (int i = 0; i < cs.length(); i++) {
            JSONObject c = cs.getJSONObject(i);
            int[] shot = ints(c.getJSONArray("screenshot")), inner = ints(c.getJSONArray("inner"));
            UiTarsImagePrep.Prepared prep = UiTarsImagePrep.prepare(png(shot[0], shot[1]), c.getInt("pad"));
            assertEquals(new int[]{prep.sendW(), prep.sendH()}, ints(c.getJSONArray("send")), c.toString(0));
            assertEquals(new int[]{prep.modelW(), prep.modelH()}, ints(c.getJSONArray("model")), c.toString(0));
            int[] dims = PngDimensions.read(prep.png());
            assertEquals(dims, new int[]{prep.modelW(), prep.modelH()}, "PNG actually padded");
            UiTarsCoords.Grid g = prep.grid(UiTarsCoords.Space.valueOf(c.getString("space")));
            JSONArray pts = c.getJSONArray("points");
            for (int p = 0; p < pts.length(); p++) {
                JSONObject pt = pts.getJSONObject(p);
                VisionAnalysisResult r = UiTarsResponseParser.parseUiTars(pt.getString("raw"), g);
                assertEquals(r.candidates().size(), 1, pt.toString());
                BoundingBox orig = prep.toOriginal(r.candidates().get(0).boundingBox());
                CssPoint css = CoordinateMapper.toCss(CoordinateMapper.center(orig), shot[0], shot[1], inner[0], inner[1]);
                assertEquals(css.x(), pt.getJSONArray("css").getDouble(0), 1.5, c.getString("space") + " pad=" + c.getInt("pad") + " " + pt);
                assertEquals(css.y(), pt.getJSONArray("css").getDouble(1), 1.5, c.getString("space") + " pad=" + c.getInt("pad") + " " + pt);
            }
        }
    }

    @Test public void paddingIsMappedAgainstPreparedSizeAndClampedToContent() {
        // 1920x842 → send 1280x561 → padded 1288x588 (Midscene: parse against preparedSize, clip to contentSize)
        UiTarsCoords.Grid padded = new UiTarsCoords.Grid(UiTarsCoords.Space.THOUSAND, 1288, 588, 1280, 561);
        UiTarsCoords.Grid unpadded = new UiTarsCoords.Grid(UiTarsCoords.Space.THOUSAND, 1280, 561, 1280, 561);
        assertEquals(padded.toContentPx(500, 500)[1], 294.0, 1e-9);
        assertEquals(unpadded.toContentPx(500, 500)[1], 280.5, 1e-9, "ignoring padding would be 13.5 sent px (~20 screenshot px) off");
        assertEquals(padded.toContentPx(999, 990)[0], 1279.0, 1e-9, "clamped out of the padding");
        assertEquals(padded.toContentPx(999, 990)[1], 560.0, 1e-9);
    }

    @Test public void uiTarsNativeShapesAreModelSpaceNeverPixels() {
        UiTarsCoords.Grid g = new UiTarsCoords.Grid(UiTarsCoords.Space.THOUSAND, 1280, 561, 1280, 561);
        // Formerly read as pixels (500,400) because it fit inside the image; it is grid → (640, 224.4).
        for (String raw : new String[]{"{\"point\":[500,400]}", "{\"click\":[500,400]}", "{\"x\":500,\"y\":400}",
                "Action: click(point='<point>500 400</point>')", "Action: click(500,400)",
                "{\"start_box\":[500,400]}", "{\"bbox\":[490,390,510,410]}", "<bbox>490 390 510 410</bbox>"}) {
            VisionAnalysisResult r = UiTarsResponseParser.parseUiTars(raw, g);
            assertEquals(r.candidates().size(), 1, raw);
            CssPoint c = CoordinateMapper.center(r.candidates().get(0).boundingBox());
            assertEquals(c.x(), 640, 1.0, raw);
            assertEquals(c.y(), 224.4, 1.0, raw);
        }
        assertTrue(UiTarsResponseParser.parseUiTars("{\"point\":[1200,10]}", g).candidates().isEmpty(), "outside grid rejected");
        // canonical candidates stay content pixels
        VisionAnalysisResult canon = UiTarsResponseParser.parseUiTars(
                "{\"candidates\":[{\"bbox\":{\"x\":100,\"y\":50,\"width\":40,\"height\":20},\"confidence\":0.9}]}", g);
        assertEquals(canon.candidates().get(0).boundingBox().x(), 100);
    }

    @Test public void legacySpaceUnknownPointRules() {
        assertTrue(UiTarsResponseParser.parse("{\"x\":1200,\"y\":50}", 800, 600).candidates().isEmpty());
        VisionAnalysisResult r = UiTarsResponseParser.parse("{\"x\":900,\"y\":50}", 800, 600);
        CssPoint c = CoordinateMapper.center(r.candidates().get(0).boundingBox());
        assertEquals(c.x(), 720.0, 0.01, "only a point that cannot be a pixel is read as grid, on both axes");
        VisionAnalysisResult px = UiTarsResponseParser.parse("{\"x\":1,\"y\":1}", 800, 600);
        assertEquals(CoordinateMapper.center(px.candidates().get(0).boundingBox()).x(), 1.0, 0.01, "integer (1,1) is a pixel, not unit space");
        VisionAnalysisResult unit = UiTarsResponseParser.parse("{\"x\":0.5,\"y\":0.25}", 800, 600);
        assertEquals(CoordinateMapper.center(unit.candidates().get(0).boundingBox()).x(), 400.0, 0.01);
    }

    @Test public void spaceConfig() {
        assertEquals(UiTarsCoords.configuredSpace(), UiTarsCoords.Space.THOUSAND);
        System.setProperty(UiTarsCoords.SPACE_KEY, "v1.5");
        assertEquals(UiTarsCoords.configuredSpace(), UiTarsCoords.Space.SMART_RESIZE_V15);
        System.setProperty(UiTarsCoords.SPACE_KEY, "bogus");
        expectThrows(IllegalArgumentException.class, UiTarsCoords::configuredSpace);
    }

    private static int[] ints(JSONArray a) {
        int[] r = new int[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.getInt(i);
        return r;
    }
}
