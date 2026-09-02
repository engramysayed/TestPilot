package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

public class UiTarsResponseParserTest {

    @Test
    public void parsesCanonicalCandidate() {
        String raw = """
                {"found":true,"candidates":[{"description":"Login button",
                "bbox":{"x":100,"y":200,"width":90,"height":36},"confidence":0.9}]}
                """;
        VisionAnalysisResult r = UiTarsResponseParser.parse(raw, 800, 600);
        Assert.assertTrue(r.found());
        Assert.assertEquals(r.candidates().size(), 1);
        Assert.assertEquals(r.candidates().get(0).boundingBox().width(), 90);
        Assert.assertEquals(r.candidates().get(0).confidence(), 0.9, 0.001);
    }

    @Test
    public void dropsZeroSizeBboxInsteadOfOneByOne() {
        String raw = """
                {"found":true,"candidates":[{"description":"Login",
                "bbox":{"x":0,"y":0,"width":0,"height":0},"confidence":0.9}]}
                """;
        VisionAnalysisResult r = UiTarsResponseParser.parse(raw, 800, 600);
        Assert.assertTrue(r.candidates().isEmpty());
        Assert.assertFalse(r.found());
    }

    @Test
    public void parsesPointAsPaddedBox() {
        String raw = """
                {"x":50,"y":60,"confidence":0.9,"description":"Login"}
                """;
        VisionAnalysisResult r = UiTarsResponseParser.parse(raw, 800, 600);
        Assert.assertTrue(r.found());
        Assert.assertEquals(r.candidates().size(), 1);
        BoundingBox b = r.candidates().get(0).boundingBox();
        Assert.assertTrue(b.width() >= 8 && b.width() <= 24, String.valueOf(b));
        Assert.assertTrue(b.height() >= 8 && b.height() <= 24, String.valueOf(b));
        Assert.assertTrue(b.x() <= 50 && b.x() + b.width() >= 50);
        Assert.assertTrue(b.y() <= 60 && b.y() + b.height() >= 60);
    }

    @Test
    public void parsesStartEndBoxNormalized() {
        // 0–1000 scale: box from (100,200) to (200,280) on 1000 grid → 80x64 on 800x600 image? 
        // Actually: x1=100/1000*800=80, y1=200/1000*600=120, x2=200/1000*800=160, y2=280/1000*600=168
        String raw = """
                {"start_box":[100,200],"end_box":[200,280],"confidence":0.88,"description":"Login"}
                """;
        VisionAnalysisResult r = UiTarsResponseParser.parse(raw, 800, 600);
        Assert.assertTrue(r.found());
        BoundingBox b = r.candidates().get(0).boundingBox();
        Assert.assertEquals(b.x(), 80);
        Assert.assertEquals(b.y(), 120);
        Assert.assertEquals(b.width(), 80);
        Assert.assertEquals(b.height(), 48);
    }

    @Test
    public void parsesXyxyArrayAbsolute() {
        String raw = """
                {"bbox":[10,20,100,60],"confidence":0.9,"description":"Submit"}
                """;
        VisionAnalysisResult r = UiTarsResponseParser.parse(raw, 800, 600);
        Assert.assertTrue(r.found());
        BoundingBox b = r.candidates().get(0).boundingBox();
        Assert.assertEquals(b.x(), 10);
        Assert.assertEquals(b.y(), 20);
        Assert.assertEquals(b.width(), 90);
        Assert.assertEquals(b.height(), 40);
    }

    @Test
    public void parsesNativeActionStartBox() {
        String raw = """
                Thought: Login button
                Action: click(start_box='<|box_start|>(500,400)<|box_end|>')
                """;
        VisionAnalysisResult r = UiTarsResponseParser.parse(raw, 1000, 1000);
        Assert.assertTrue(r.found());
        BoundingBox b = r.candidates().get(0).boundingBox();
        Assert.assertTrue(b.x() <= 500 && b.x() + b.width() >= 500, String.valueOf(b));
        Assert.assertTrue(b.y() <= 400 && b.y() + b.height() >= 400, String.valueOf(b));
        Assert.assertTrue(b.width() >= 8);
    }

    @Test
    public void parsesPointTagAction() {
        String raw = "Action: click(point='<point>200 300</point>')";
        VisionAnalysisResult r = UiTarsResponseParser.parse(raw, 1000, 1000);
        Assert.assertTrue(r.found());
        BoundingBox b = r.candidates().get(0).boundingBox();
        Assert.assertTrue(b.x() <= 200 && b.x() + b.width() >= 200, String.valueOf(b));
    }
}
