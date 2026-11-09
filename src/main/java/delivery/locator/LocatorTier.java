package delivery.locator;

/** Stability family of one locator, independent of how it will be ranked. */
public enum LocatorTier {
    PREFERRED_HOOK,
    TEST_ID,
    /** Unique id that a developer wrote ({@code GeneratedIdDetector} says it is not framework-minted). */
    ID,
    /** Unique {@code name} attribute (form controls), not framework-minted. */
    NAME,
    /** ARIA role + accessible name ({@code [role=button][aria-label=…]}, {@code button[aria-label=…]}). */
    ROLE_NAME,
    /** Label-like human text attribute or label anchor: aria-label, placeholder, title, alt, {@code //label[…]}. */
    LABEL,
    /** Visible-text XPath ({@code normalize-space}, {@code text()}), {@code text=} keel targets. */
    TEXT,
    /** Other attribute CSS ({@code tag[attr='v']}). */
    CSS_ATTR,
    /** Other attribute XPath ({@code //tag[@attr='v']}). */
    XPATH_ATTR,
    /** Positional: nth-child, {@code (//x)[3]}, {@code [2]}. */
    ORDINAL,
    /** Generated id/class, absolute path, or unparseable. */
    BRITTLE
}
