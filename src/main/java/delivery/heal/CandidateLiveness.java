package delivery.heal;

/** Live interactability of one shortlist row (Selenium probe). */
public record CandidateLiveness(boolean displayed, boolean enabled, int width, int height) {
    public boolean interactable() {
        return displayed && enabled && width > 0 && height > 0;
    }

    public static CandidateLiveness dead() {
        return new CandidateLiveness(false, false, 0, 0);
    }
}
