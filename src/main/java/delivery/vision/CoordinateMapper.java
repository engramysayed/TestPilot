package delivery.vision;

record CssPoint(double x, double y) {
}

public final class CoordinateMapper {

    private CoordinateMapper() {
    }

    public static CssPoint center(BoundingBox box) {
        return new CssPoint(
                box.x() + box.width() / 2.0,
                box.y() + box.height() / 2.0);
    }

    public static CssPoint toCss(
            CssPoint imagePoint,
            int imageWidth,
            int imageHeight,
            int viewportWidth,
            int viewportHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            return imagePoint;
        }
        double scaleX = (double) viewportWidth / imageWidth;
        double scaleY = (double) viewportHeight / imageHeight;
        return new CssPoint(imagePoint.x() * scaleX, imagePoint.y() * scaleY);
    }
}
