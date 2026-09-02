"""Generate amazon-smoke-e2e.xlsx for public Amazon.com browse / cart smoke.

Aligned with TestPilot Excel rules:
- Required: TC_ID, Title, Steps, ExpectedResult
- Recommended: Preconditions
- Optional: Priority, Tags, VisualAssertion, TestData
- Typed values in TestData (line-aligned with Steps). Blank → invent at prove.
- No login / no secrets / no file upload / no checkout payment

Notes:
- Amazon A/B UI and anti-bot can shift labels and CAPTCHA. Cases stay on
  observable public flows: home, search, product detail, cart.
- Prefer short, common UI phrases so both desktop and mobile layouts can match.
- Base URL for the job: https://www.amazon.com/
"""
from pathlib import Path

from openpyxl import Workbook

OUT = Path(__file__).resolve().parents[1] / "src/test/resources/delivery/amazon-smoke-e2e.xlsx"


def steps(*lines: str) -> str:
    return "\n".join(lines)


def testdata(*lines: str) -> str:
    return "\n".join(lines)


def main() -> None:
    wb = Workbook()
    ws = wb.active
    ws.title = "ManualTCs"
    headers = [
        "TC_ID",
        "Title",
        "Preconditions",
        "Steps",
        "ExpectedResult",
        "Priority",
        "Tags",
        "VisualAssertion",
        "TestData",
    ]
    ws.append(headers)

    rows = [
        (
            "TC_AMZ_01",
            "Home page shows search controls",
            "No login required. Public Amazon home. "
            "Base URL is https://www.amazon.com/. "
            "Cookie or location banners may appear — dismiss only if they block the search box.",
            steps(
                "1. Open the home page at /",
                "2. Confirm the search box is visible",
                "3. Confirm the Search button is visible",
            ),
            steps(
                "1. Amazon home is shown",
                "2. Search box is visible",
                "3. Search button is visible",
            ),
            "P1",
            "smoke,home,amazon",
            "Amazon home shows a search box and Search control.",
            "",
        ),
        (
            "TC_AMZ_02",
            "Search for wireless mouse returns results",
            "No login required. Public search results. "
            "Results copy and layout may vary by region and A/B.",
            steps(
                "1. Open the home page at /",
                "2. Enter in the search box",
                "3. Click the Search button",
                "4. Confirm the text results is visible",
            ),
            steps(
                "1. Amazon home is shown",
                "2. Search box accepts the entered query",
                "3. Search starts",
                "4. A results heading or results list is shown",
            ),
            "P1",
            "smoke,search,amazon",
            "Search results page is visible after searching for wireless mouse.",
            testdata(
                "",
                "wireless mouse",
                "",
                "",
            ),
        ),
        (
            "TC_AMZ_03",
            "Open first search result product page",
            "No login required. Starts from a search for headphones. "
            "Product titles change often — stop at a product detail page with Add to Cart.",
            steps(
                "1. Open the home page at /",
                "2. Enter in the search box",
                "3. Click the Search button",
                "4. Confirm the text results is visible",
                "5. Click the first product title link",
                "6. Confirm the Add to Cart button is visible",
            ),
            steps(
                "1. Amazon home is shown",
                "2. Search box accepts the entered query",
                "3. Search starts",
                "4. Results are shown",
                "5. A product detail page opens",
                "6. Add to Cart is visible",
            ),
            "P1",
            "smoke,pdp,amazon",
            "A product detail page is shown with an Add to Cart control.",
            testdata(
                "",
                "wired headphones",
                "",
                "",
                "",
                "",
            ),
        ),
        (
            "TC_AMZ_04",
            "Add product to cart from search",
            "No login required. Public add-to-cart. "
            "After Add to Cart, Amazon may show a confirmation tray or cart page — "
            "assert that Cart is reachable.",
            steps(
                "1. Open the home page at /",
                "2. Enter in the search box",
                "3. Click the Search button",
                "4. Click the first product title link",
                "5. Confirm the Add to Cart button is visible",
                "6. Click the Add to Cart button",
                "7. Confirm the text Cart is visible",
            ),
            steps(
                "1. Amazon home is shown",
                "2. Search box accepts the entered query",
                "3. Search starts",
                "4. Product detail opens",
                "5. Add to Cart is visible",
                "6. Add to Cart is clicked",
                "7. Cart wording or cart entry is visible",
            ),
            "P1",
            "smoke,cart,amazon",
            "After Add to Cart, cart-related UI is visible on screen.",
            testdata(
                "",
                "usb c cable",
                "",
                "",
                "",
                "",
                "",
            ),
        ),
        (
            "TC_AMZ_05",
            "Open cart from home",
            "No login required. Cart may be empty. "
            "Do not proceed to checkout or payment.",
            steps(
                "1. Open the home page at /",
                "2. Confirm the Cart link is visible",
                "3. Click the Cart link",
                "4. Confirm the text Cart is visible",
            ),
            steps(
                "1. Amazon home is shown",
                "2. Cart control is visible",
                "3. Cart is opened",
                "4. Cart page or cart panel is shown",
            ),
            "P2",
            "smoke,cart,amazon",
            "Cart page or cart panel is visible.",
            "",
        ),
        (
            "TC_AMZ_06",
            "Search then clear and search again",
            "No login required. Two public searches without login.",
            steps(
                "1. Open the home page at /",
                "2. Enter in the search box",
                "3. Click the Search button",
                "4. Confirm the text results is visible",
                "5. Enter in the search box",
                "6. Click the Search button",
                "7. Confirm the text results is visible",
            ),
            steps(
                "1. Amazon home is shown",
                "2. First query is entered",
                "3. First search starts",
                "4. First results are shown",
                "5. Second query is entered",
                "6. Second search starts",
                "7. Second results are shown",
            ),
            "P2",
            "search,regression,amazon",
            "Amazon shows a results page after the second search.",
            testdata(
                "",
                "notebook",
                "",
                "",
                "ballpoint pen",
                "",
                "",
            ),
        ),
        (
            "TC_AMZ_07",
            "Today's Deals entry is reachable from home",
            "No login required. Nav labels may say Today's Deals or Deals. "
            "If the link is missing in a region, mark the case for revise — do not invent another site.",
            steps(
                "1. Open the home page at /",
                "2. Confirm the text Deals is visible",
                "3. Click the Deals link",
                "4. Confirm the text Deals is visible",
            ),
            steps(
                "1. Amazon home is shown",
                "2. A Deals entry is visible in the chrome or body",
                "3. Deals is opened",
                "4. A deals landing page remains visible",
            ),
            "P3",
            "nav,amazon",
            "A Deals-related page is visible after opening Deals.",
            "",
        ),
    ]

    for row in rows:
        ws.append(list(row))

    for col, width in zip("ABCDEFGHI", [14, 44, 52, 64, 48, 10, 28, 56, 36]):
        ws.column_dimensions[col].width = width

    OUT.parent.mkdir(parents=True, exist_ok=True)
    wb.save(OUT)
    print(f"wrote {OUT}")
    for row in rows:
        print(f"  {row[0]} — {row[1]}")


if __name__ == "__main__":
    main()
