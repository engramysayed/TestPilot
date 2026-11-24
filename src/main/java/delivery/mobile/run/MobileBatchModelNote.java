package delivery.mobile.run;

import delivery.authoring.LocalLlmClient;

import java.time.Duration;

/**
 * One sentence from the local model after the written batch review.
 * A down or slow model leaves the written sentence unchanged.
 */
public final class MobileBatchModelNote {

    private MobileBatchModelNote() {
    }

    public static String append(String review, String baseUrl, String model) {
        if (review == null || review.isBlank() || baseUrl == null || baseUrl.isBlank()
                || model == null || model.isBlank()) {
            return review == null ? "" : review;
        }
        try {
            LocalLlmClient client = new LocalLlmClient(baseUrl, model, Duration.ofSeconds(25), 160);
            String note = client.completeChat(
                    "You review a finished phone test batch. Reply with one sentence a tester can read. "
                            + "Use only facts in the text. Do not invent steps.",
                    review,
                    false);
            return join(review, note);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return review;
        } catch (Exception e) {
            return review;
        }
    }

    static String join(String review, String note) {
        if (review == null || review.isBlank()) {
            return review == null ? "" : review;
        }
        if (note == null) {
            return review;
        }
        String one = note.trim();
        int line = one.indexOf('\n');
        if (line >= 0) {
            one = one.substring(0, line).trim();
        }
        one = one.replaceAll("\\s+", " ");
        if (one.length() > 400) {
            one = one.substring(0, 400).trim();
        }
        if (one.isEmpty() || one.equals(review)) {
            return review;
        }
        return review + " Model: " + one;
    }
}
