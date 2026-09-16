package delivery.job;

import delivery.ir.TcIdentity;

/**
 * Evidence identity for one execution of a logical TC ID.
 * Repeated Call-before rows in an expanded run list must not overwrite one folder.
 */
public final class OccurrenceIdentity {

    private OccurrenceIdentity() {
    }

    public static String folder(String tcId, int occurrence) {
        String id = TcIdentity.storageKey(tcId == null || tcId.isBlank() ? "tc" : tcId);
        int n = Math.max(1, occurrence);
        return id + "__occ_" + n;
    }
}
