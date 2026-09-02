"""Generate the-internet-confirm-6tc.xlsx (no secrets in cells)."""
from pathlib import Path

from openpyxl import Workbook

OUT = Path(__file__).resolve().parents[1] / "src/test/resources/delivery/the-internet-confirm-6tc.xlsx"


def main() -> None:
    wb = Workbook()
    ws = wb.active
    ws.title = "ManualTCs"
    headers = ["TC_ID", "Title", "Preconditions", "Steps", "ExpectedResult", "Priority", "Tags"]
    ws.append(headers)

    rows = [
        (
            "TC_TI_01",
            "Valid login reaches Secure Area",
            "Base URL is https://the-internet.herokuapp.com/. "
            "Pass TARGET username/password via the job (public Form Authentication demo).",
            "\n".join([
                "1. Open the Login Page at /login",
                "2. Enter the username",
                "3. Enter the password",
                "4. Click the Login button",
                "5. Confirm the text Secure Area is visible",
                "6. Confirm the Logout button is visible",
            ]),
            "User reaches Secure Area and Logout is shown",
            "P1",
            "smoke,login",
        ),
        (
            "TC_TI_02",
            "Login fails with wrong password",
            "Negative login case. Username is the public Form Authentication demo user; "
            "password wrongPassword must fail (no job login prelude).",
            "\n".join([
                "1. Open the Login Page at /login",
                "2. Enter the username tomsmith",
                "3. Enter the password wrongPassword",
                "4. Click the Login button",
                "5. Confirm the text Your password is invalid is visible",
            ]),
            "Error Your password is invalid is shown and user is not logged in",
            "P1",
            "smoke,login,negative",
        ),
        (
            "TC_TI_03",
            "Add Element then remove it",
            "No login required. Public page — auth not needed.",
            "\n".join([
                "1. Open the Add/Remove Elements page at /add_remove_elements/",
                "2. Confirm the text Add/Remove Elements is visible",
                "3. Click the Add Element button",
                "4. Confirm the Delete button is visible",
                "5. Click the Delete button",
                "6. Confirm the Delete button is not visible",
            ]),
            "\n".join([
                "1. Add/Remove Elements page is shown",
                "2. Heading Add/Remove Elements is visible",
                "3. A Delete button appears after Add Element",
                "4. Delete button is visible",
                "5. Delete removes the element",
                "6. Delete button is no longer visible",
            ]),
            "P1",
            "smoke,dynamic",
        ),
        (
            "TC_TI_04",
            "Select Option 2 from dropdown",
            "No login required. Public page — auth not needed.",
            "\n".join([
                "1. Open the Dropdown page at /dropdown",
                "2. Confirm the text Dropdown List is visible",
                "3. Select Option 2 from the dropdown",
                "4. Confirm Option 2 is the selected value",
            ]),
            "Dropdown shows Option 2 as selected",
            "P2",
            "smoke,dropdown",
        ),
        (
            "TC_TI_05",
            "Remove checkbox on Dynamic Controls",
            "No login required. Public page — auth not needed.",
            "\n".join([
                "1. Open the Dynamic Controls page at /dynamic_controls",
                "2. Confirm the text Dynamic Controls is visible",
                "3. Click the Remove button",
                "4. Confirm the text It's gone is visible",
            ]),
            "Checkbox is removed and It's gone message is shown",
            "P1",
            "smoke,dynamic,wait",
        ),
        (
            "TC_TI_06",
            "Dynamic Loading shows Hello World",
            "No login required. Public page — auth not needed.",
            "\n".join([
                "1. Open the Dynamic Loading Example 1 page at /dynamic_loading/1",
                "2. Confirm the text Example 1 is visible",
                "3. Click the Start button",
                "4. Confirm the text Hello World! is visible",
            ]),
            "After loading finishes, Hello World! is displayed",
            "P1",
            "smoke,wait,dynamic",
        ),
    ]

    for row in rows:
        ws.append(list(row))

    for col, width in zip("ABCDEFG", [12, 36, 40, 60, 40, 10, 22]):
        ws.column_dimensions[col].width = width

    OUT.parent.mkdir(parents=True, exist_ok=True)
    wb.save(OUT)
    print(f"wrote {OUT}")
    for row in rows:
        print(f"  {row[0]} — {row[1]}")


if __name__ == "__main__":
    main()
