package delivery.mobile.runner.action;

import delivery.mobile.runner.snapshot.MobileSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class FakeMobileDriver implements MobileDriverPort {
    private Supplier<MobileSnapshot> source;
    private String typed = "";
    private boolean transportFail;
    private boolean staleOnTap;
    private byte[] shot = new byte[16];
    private final List<String> typePathsUsed = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();
    private final AtomicBoolean keyboardShown = new AtomicBoolean(false);
    private boolean escDismisses = true;
    private boolean backDismisses = true;
    private boolean cancelled;
    private String orientation = "PORTRAIT";
    private int windowW = 1080;
    private int windowH = 1920;
    private boolean lastRestartClearedData;
    private int restartCount;
    private int swipeCount;
    private boolean clearDataAttempted;

    FakeMobileDriver(Supplier<MobileSnapshot> source) {
        this.source = source;
    }

    void setSource(Supplier<MobileSnapshot> s) { this.source = s; }
    void setTransportFail(boolean v) { this.transportFail = v; }
    void setStaleOnTap(boolean v) { this.staleOnTap = v; }
    void setShot(byte[] s) { this.shot = s; }
    String typed() { return typed; }
    List<String> typePathsUsed() { return typePathsUsed; }
    List<String> actions() { return List.copyOf(actions); }
    void setKeyboardShown(boolean v) { keyboardShown.set(v); }
    void setEscDismisses(boolean v) { escDismisses = v; }
    void setBackDismisses(boolean v) { backDismisses = v; }
    void setCancelled(boolean v) { cancelled = v; }
    void setOrientation(String o) { orientation = o; }
    void setWindow(int w, int h) { windowW = w; windowH = h; }
    int swipeCount() { return swipeCount; }
    int restartCount() { return restartCount; }
    boolean lastRestartClearedData() { return lastRestartClearedData; }
    boolean clearDataAttempted() { return clearDataAttempted; }

    @Override
    public MobileSnapshot pageSource() {
        return source.get();
    }

    @Override
    public void tap(int nodeIndex) throws DriverException {
        if (transportFail) throw new DriverException(DriverException.Kind.TRANSPORT, "boom");
        if (staleOnTap) throw new DriverException(DriverException.Kind.STALE, "stale");
        actions.add("TAP:" + nodeIndex);
    }

    @Override
    public void longPress(int nodeIndex) throws DriverException {
        if (transportFail) throw new DriverException(DriverException.Kind.TRANSPORT, "boom");
        actions.add("LONG_PRESS:" + nodeIndex);
    }

    @Override
    public void clear(int nodeIndex) { typed = ""; actions.add("CLEAR"); }

    @Override
    public void typeUnicode(int nodeIndex, String value) {
        typePathsUsed.add("UNICODE");
        typed = value;
    }

    @Override
    public void typeAscii(int nodeIndex, String value) {
        typePathsUsed.add("ASCII");
        typed = value;
    }

    @Override
    public String readText(int nodeIndex) {
        return typed;
    }

    @Override
    public byte[] screenshot() {
        return shot;
    }

    @Override
    public void pressBack() {
        actions.add("BACK");
    }

    @Override
    public void pressKeyCode(int keyCode) {
        actions.add("KEY:" + keyCode);
        if (keyCode == KEYCODE_ESC && escDismisses) {
            keyboardShown.set(false);
        }
        if (keyCode == KEYCODE_BACK && backDismisses) {
            keyboardShown.set(false);
        }
    }

    @Override
    public boolean isKeyboardShown() {
        return keyboardShown.get();
    }

    @Override
    public void swipeNormalized(NormalizedSwipe swipe) {
        swipe.toDevicePx(windowW, windowH);
        swipeCount++;
        actions.add("SWIPE:" + swipe.startX() + "," + swipe.startY() + "->" + swipe.endX() + "," + swipe.endY());
    }

    @Override
    public void launchApp(String packageName, String activity) {
        actions.add("LAUNCH:" + packageName + "/" + activity);
    }

    @Override
    public void restartApp(String packageName, boolean clearData) {
        restartCount++;
        lastRestartClearedData = clearData;
        if (clearData) {
            clearDataAttempted = true;
        }
        actions.add("RESTART:" + packageName + ":clearData=" + clearData);
    }

    @Override
    public int windowWidth() { return windowW; }

    @Override
    public int windowHeight() { return windowH; }

    @Override
    public String orientation() { return orientation; }

    @Override
    public boolean isActionCancelled() { return cancelled; }
}
