package delivery.mobile.runner.action;

import delivery.mobile.runner.snapshot.MobileBounds;

/** Target visibility inside a viewport/container (M3-04 / SCROLL_UNTIL). */
public final class VisibilityPercent {
    private VisibilityPercent() {
    }

    public static int of(MobileBounds target, MobileBounds viewport) {
        if (target == null || viewport == null || target.isEmpty() || viewport.isEmpty()) {
            return 0;
        }
        int l = Math.max(target.left(), viewport.left());
        int t = Math.max(target.top(), viewport.top());
        int r = Math.min(target.right(), viewport.right());
        int b = Math.min(target.bottom(), viewport.bottom());
        if (r <= l || b <= t) {
            return 0;
        }
        long inter = (long) (r - l) * (b - t);
        long area = (long) target.width() * target.height();
        if (area <= 0) {
            return 0;
        }
        return (int) Math.min(100, (inter * 100) / area);
    }
}
