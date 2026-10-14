package delivery.mobile.transport;

import keel.contract.runner.ArtifactSignature;

/**
 * HMAC verification with body size caps for runner uploads (M1-05).
 */
public final class MobileHmacLimits {
    private MobileHmacLimits() {
    }

    public static void requireBodyWithinLimit(byte[] body, long maxBytes) {
        long len = body == null ? 0L : body.length;
        if (len > maxBytes) {
            throw new MobileTransportException(MobileTransportErrorCode.HMAC_LIMIT,
                    "body exceeds HMAC size limit " + maxBytes);
        }
    }

    public static boolean verifyArtifact(String token, String jobId, String attemptId,
                                         byte[] body, String signatureHeader, long maxBytes) {
        requireBodyWithinLimit(body, maxBytes);
        return ArtifactSignature.matches(token, jobId, attemptId, body, signatureHeader);
    }
}
