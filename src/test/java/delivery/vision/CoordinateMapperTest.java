package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CoordinateMapperTest {

    @Test
    public void centerOfBox() {
        CssPoint p = CoordinateMapper.center(new BoundingBox(400, 500, 120, 40));
        Assert.assertEquals(p.x(), 460.0, 0.01);
        Assert.assertEquals(p.y(), 520.0, 0.01);
    }

    @Test
    public void scalesBitmapToViewport() {
        CssPoint img = new CssPoint(200, 100);
        CssPoint css = CoordinateMapper.toCss(img, 2000, 1000, 1000, 500);
        Assert.assertEquals(css.x(), 100.0, 0.01);
        Assert.assertEquals(css.y(), 50.0, 0.01);
    }
}
