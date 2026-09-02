package delivery.heal;

/**
 * A locator that already failed for the current Excel intent. Heal must not retry it
 * (or the same control under another strategy).
 */
public record FailedLocator(String strategy, String value, String errorSummary) {
}
