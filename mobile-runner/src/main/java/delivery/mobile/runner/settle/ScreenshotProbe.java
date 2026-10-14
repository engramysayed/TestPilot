package delivery.mobile.runner.settle;

/** Optional raw screenshot bytes (PNG/RGBA) for visual diff. */
@FunctionalInterface
public interface ScreenshotProbe {
    byte[] capture();
}
