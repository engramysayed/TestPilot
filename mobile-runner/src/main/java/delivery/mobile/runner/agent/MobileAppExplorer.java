package delivery.mobile.runner.agent;

import delivery.mobile.runner.action.MobileDriverPort;
import delivery.mobile.runner.action.NormalizedSwipe;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Opens the app and taps controls it has not tried. Does not type.
 * shortcut: taps and crash dialogs only. Upgrade when the hunt should fill fields.
 */
public final class MobileAppExplorer {

    public record Finding(String kind, String detail) {
    }

    public List<Finding> explore(MobileDriverPort driver, String pkg, String activity, int cap,
                                 BooleanSupplier cancel) {
        List<Finding> out = new ArrayList<>();
        int limit = Math.max(1, Math.min(cap, 40));
        try {
            if (activity == null || activity.isBlank()) {
                driver.restartApp(pkg, false);
            } else {
                driver.launchApp(pkg, activity);
            }
        } catch (Exception e) {
            out.add(new Finding("BUG", "The app did not open. " + safe(e.getMessage())));
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        int taps = 0;
        int stuck = 0;
        while (taps < limit && stuck < 2) {
            if (cancel != null && cancel.getAsBoolean() || driver.isActionCancelled()) {
                out.add(new Finding("STOP", "Stopped."));
                return out;
            }
            MobileSnapshot snap;
            try {
                snap = driver.pageSource();
            } catch (RuntimeException e) {
                out.add(new Finding("BUG", "The screen could not be read. " + safe(e.getMessage())));
                return out;
            }
            if (snap == null || snap.nodes() == null) {
                out.add(new Finding("BUG", "The screen could not be read."));
                return out;
            }
            String crash = MobileExplorePicker.crashText(snap.nodes());
            if (!crash.isBlank()) {
                out.add(new Finding("BUG", crash));
                try {
                    driver.pressBack();
                } catch (Exception ignored) {
                    return out;
                }
                continue;
            }
            int index = MobileExplorePicker.nextTap(snap.nodes(), seen);
            if (index < 0) {
                stuck++;
                try {
                    driver.swipeNormalized(new NormalizedSwipe(0.5, 0.75, 0.5, 0.30, 280));
                } catch (Exception e) {
                    out.add(new Finding("NOTE", "Nothing new to tap."));
                    return out;
                }
                continue;
            }
            MobileSnapshotNode node = snap.node(index);
            String key = MobileExplorePicker.key(node);
            seen.add(key);
            try {
                driver.tap(index);
                taps++;
                out.add(new Finding("TAP", MobileExplorePicker.label(node)));
                stuck = 0;
            } catch (Exception e) {
                out.add(new Finding("BUG", "Tap failed on " + MobileExplorePicker.label(node) + ". " + safe(e.getMessage())));
                return out;
            }
        }
        if (taps == 0) {
            out.add(new Finding("NOTE", "No tappable control was on screen."));
        }
        return out;
    }

    private static String safe(String message) {
        if (message == null || message.isBlank()) {
            return "No detail.";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
