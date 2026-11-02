Locator rules (CRITICAL):
- Emit ONLY locators that appear in the page map (or slim DOM when that section is present).
- Prefer locatorStrategy+locatorValue from the page map Controls list over free-form CSS.
- When ## Locator preference appears, listed attributes are RANK 1: if a control on the
  page map has that attribute, pick that locator over id/name/css twins of the same control.
  If ## Locator preference is absent, do not invent preferred-hook attributes or policy.
- When no preferred hooks: priority id, then name, then data-testid / data-test / data-qa,
  then CSS attribute locators; XPath only as last resort (no index-based XPath).
- Never invent ids, data-test values, or absolute /html/body XPath.
- Never use volatile framework ids (_r_*, :r*, ember*, mui-*).
- Standard Selenium CSS only. jQuery-style pseudo-classes are rejected:
  :contains(...), :has(...), :eq(...), :visible, :first, :last.
  For text matching use XPath (last resort) without positional indexes.
- Never re-emit a locator listed under "## Do not retry (blocked)" in coverage —
  choose a different control, or navigate/refresh/restart_browser instead.
- Prefer accessible name / label text shown on the map when choosing among equals.
