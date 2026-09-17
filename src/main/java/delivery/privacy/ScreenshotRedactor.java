package delivery.privacy;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Paints over password-field pixels in PNG captures before evidence, heal, or vision dispatch.
 */
public final class ScreenshotRedactor {
    private static final String PASSWORD_BOXES_JS = """
            const dpr = window.devicePixelRatio || 1;
            const els = document.querySelectorAll(
              'input[type=password], input[autocomplete=current-password], input[autocomplete=new-password]');
            const boxes = [];
            els.forEach((el) => {
              const r = el.getBoundingClientRect();
              if (r.width <= 0 || r.height <= 0) return;
              boxes.push({
                x: Math.floor(r.x * dpr) - 1,
                y: Math.floor(r.y * dpr) - 1,
                w: Math.ceil(r.width * dpr) + 2,
                h: Math.ceil(r.height * dpr) + 2
              });
            });
            return boxes;
            """;

    public record Box(int x, int y, int w, int h) {
    }

    private ScreenshotRedactor() {
    }

    public static byte[] redactCapture(WebDriver driver, byte[] png) {
        if (png == null || png.length == 0) {
            return png;
        }
        return redact(png, locatePasswordBoxes(driver));
    }

    public static List<Box> locatePasswordBoxes(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor js)) {
            return List.of();
        }
        try {
            Object raw = js.executeScript(PASSWORD_BOXES_JS);
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<Box> boxes = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    boxes.add(new Box(intVal(map.get("x")), intVal(map.get("y")),
                            intVal(map.get("w")), intVal(map.get("h"))));
                }
            }
            return boxes;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public static byte[] redact(byte[] png, List<Box> boxes) {
        if (png == null) {
            return null;
        }
        if (png.length == 0 || boxes == null || boxes.isEmpty()) {
            return png;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
            if (src == null) {
                return png;
            }
            Graphics2D g = src.createGraphics();
            g.setColor(Color.BLACK);
            for (Box box : boxes) {
                if (box == null || box.w() <= 0 || box.h() <= 0) {
                    continue;
                }
                int x = Math.max(0, box.x());
                int y = Math.max(0, box.y());
                int w = Math.min(src.getWidth() - x, box.w());
                int h = Math.min(src.getHeight() - y, box.h());
                if (w > 0 && h > 0) {
                    g.fillRect(x, y, w, h);
                }
            }
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(src, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return png;
        }
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
