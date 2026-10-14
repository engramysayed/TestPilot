package delivery.mobile.runner.bind;

import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Stable hierarchy fingerprint excluding system-bar and IME nodes so status-bar clock/signal
 * changes do not invalidate binding identity (acceptance B13).
 */
public final class BindingFingerprint {
    private BindingFingerprint() {}

    public static String of(MobileSnapshot snap) {
        StringBuilder sb = new StringBuilder();
        for (MobileSnapshotNode n : snap.nodes()) {
            if (!NodeEligibility.isAnchorEligible(n, snap)) continue;
            sb.append(n.className()).append('|')
                    .append(n.resourceId()).append('|')
                    .append(MatchNormalizer.normalize(n.contentDesc())).append('|')
                    .append(MatchNormalizer.normalize(n.password() ? "" : n.rawText())).append('|')
                    .append(n.bounds() == null ? "" : n.bounds().toString()).append(';');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
