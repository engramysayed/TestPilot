"""Generate facebook-reg-e2e.xlsx for the public Meta/Facebook registration form.

Aligned with the portal TC guide (tc-guide.html):
- Required columns: TC_ID, Title, Steps, ExpectedResult
- Recommended: Preconditions
- Optional: Priority, Tags, VisualAssertion, TestData
- Typed values live in TestData (line-aligned with Steps). Blank TestData → invent at prove.
- Exact UI labels from https://www.facebook.com/reg/
- No login / no secrets / no file upload

Note: After Submit, Facebook often shows CAPTCHA or verification.
Cases stop at observable form behaviour so TestPilot can convert them.

Automated Edge (failure evidence 2026-08-13) showed heading "Get started on Facebook"
not "Get started on Facebook with a Meta account". Assert the shorter phrase so both
A/B variants still match body text.
"""
from pathlib import Path

from openpyxl import Workbook

OUT = Path(__file__).resolve().parents[1] / "src/test/resources/delivery/facebook-reg-e2e.xlsx"


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
            "TC_FB_REG_01",
            "Registration page shows all form fields",
            "No login required. Public registration page at /reg/. "
            "Base URL is https://www.facebook.com/. "
            "Automated browser heading is Get started on Facebook.",
            steps(
                "1. Open the registration page at /reg/",
                "2. Confirm the text Get started on Facebook is visible",
                "3. Confirm the First name field is visible",
                "4. Confirm the Surname field is visible",
                "5. Confirm the Day dropdown is visible",
                "6. Confirm the Month dropdown is visible",
                "7. Confirm the Year dropdown is visible",
                "8. Confirm the Select your gender dropdown is visible",
                "9. Confirm the Mobile number or email address field is visible",
                "10. Confirm the Password field is visible",
            ),
            steps(
                "1. Registration page is shown",
                "2. Heading Get started on Facebook is visible",
                "3. First name field is visible",
                "4. Surname field is visible",
                "5. Day dropdown is visible",
                "6. Month dropdown is visible",
                "7. Year dropdown is visible",
                "8. Select your gender dropdown is visible",
                "9. Mobile number or email address field is visible",
                "10. Password field is visible",
            ),
            "P1",
            "smoke,reg,facebook",
            "Facebook registration form is visible with First name, Surname, date of birth, gender, mobile or email, and password fields.",
            "",
        ),
        (
            "TC_FB_REG_02",
            "Fill registration form and click Submit",
            "No login required. Public registration page at /reg/. "
            "Use disposable test values only — do not use a real personal account. "
            "After Submit, Facebook may show CAPTCHA or verification; this case ends at Submit.",
            steps(
                "1. Open the registration page at /reg/",
                "2. Confirm the text Get started on Facebook is visible",
                "3. Enter in the First name field",
                "4. Enter in the Surname field",
                "5. Select 15 from the Day dropdown",
                "6. Select Jan from the Month dropdown",
                "7. Select 1995 from the Year dropdown",
                "8. Select Female from the Select your gender dropdown",
                "9. Enter in the Mobile number or email address field",
                "10. Enter in the Password field",
                "11. Click the Submit button",
            ),
            steps(
                "1. Registration page is shown",
                "2. Heading Get started on Facebook is visible",
                "3. First name field accepts the entered value",
                "4. Surname field accepts the entered value",
                "5. Day shows 15 as selected",
                "6. Month shows Jan as selected",
                "7. Year shows 1995 as selected",
                "8. Gender shows Female as selected",
                "9. Email field accepts the entered address",
                "10. Password field accepts the entered password",
                "11. Submit is clicked and the form leaves the empty starting state "
                "(validation message, CAPTCHA, or next Meta step may appear)",
            ),
            "P1",
            "smoke,reg,e2e,facebook",
            "After Submit the page is not a blank error: a CAPTCHA, verification, next Meta step, or the filled registration form is visible.",
            testdata(
                "",
                "",
                "Nora",
                "Bennett",
                "",
                "",
                "",
                "",
                "nora.bennett.reg02@example.com",
                "Harbor!Pass234",
                "",
            ),
        ),
        (
            "TC_FB_REG_03",
            "Submit empty registration form shows validation",
            "No login required. Public registration page at /reg/. "
            "Negative case — leave all fields empty.",
            steps(
                "1. Open the registration page at /reg/",
                "2. Confirm the text Get started on Facebook is visible",
                "3. Confirm the First name field is visible",
                "4. Click the Submit button",
                "5. Confirm the First name field is visible",
                "6. Confirm the Mobile number or email address field is visible",
                "7. Confirm the Password field is visible",
            ),
            steps(
                "1. Registration page is shown",
                "2. Heading Get started on Facebook is visible",
                "3. First name field is visible",
                "4. Empty submit does not create an account",
                "5. User remains on the registration form with First name still shown",
                "6. Mobile number or email address field remains visible",
                "7. Password field remains visible",
            ),
            "P1",
            "smoke,reg,negative,facebook",
            "After empty Submit the registration form is still shown with First name, mobile or email, and Password fields visible.",
            "",
        ),
        (
            "TC_FB_REG_04",
            "Submit without email or mobile shows validation",
            "No login required. Public registration page at /reg/. "
            "Negative case — omit contact field.",
            steps(
                "1. Open the registration page at /reg/",
                "2. Confirm the text Get started on Facebook is visible",
                "3. Enter in the First name field",
                "4. Enter in the Surname field",
                "5. Select 10 from the Day dropdown",
                "6. Select Mar from the Month dropdown",
                "7. Select 1990 from the Year dropdown",
                "8. Select Male from the Select your gender dropdown",
                "9. Enter in the Password field",
                "10. Click the Submit button",
                "11. Confirm the Mobile number or email address field is visible",
            ),
            steps(
                "1. Registration page is shown",
                "2. Heading Get started on Facebook is visible",
                "3. First name accepts the entered value",
                "4. Surname accepts the entered value",
                "5. Day shows 10 as selected",
                "6. Month shows Mar as selected",
                "7. Year shows 1990 as selected",
                "8. Gender shows Male as selected",
                "9. Password accepts the entered value",
                "10. Submit does not complete registration without contact info",
                "11. Mobile number or email address field remains visible on the form",
            ),
            "P1",
            "reg,negative,validation,facebook",
            "After Submit without contact info the registration form is still shown and the Mobile number or email address field is visible.",
            testdata(
                "",
                "",
                "Alex",
                "Smith",
                "",
                "",
                "",
                "",
                "Harbor!Pass234",
                "",
                "",
            ),
        ),
        (
            "TC_FB_REG_05",
            "Submit without password shows validation",
            "No login required. Public registration page at /reg/. "
            "Negative case — omit password.",
            steps(
                "1. Open the registration page at /reg/",
                "2. Confirm the text Get started on Facebook is visible",
                "3. Enter in the First name field",
                "4. Enter in the Surname field",
                "5. Select 20 from the Day dropdown",
                "6. Select Jun from the Month dropdown",
                "7. Select 1992 from the Year dropdown",
                "8. Select Female from the Select your gender dropdown",
                "9. Enter in the Mobile number or email address field",
                "10. Click the Submit button",
                "11. Confirm the Password field is visible",
            ),
            steps(
                "1. Registration page is shown",
                "2. Heading Get started on Facebook is visible",
                "3. First name accepts the entered value",
                "4. Surname accepts the entered value",
                "5. Day shows 20 as selected",
                "6. Month shows Jun as selected",
                "7. Year shows 1992 as selected",
                "8. Gender shows Female as selected",
                "9. Email accepts the entered address",
                "10. Submit does not complete registration without a password",
                "11. Password field remains visible on the form",
            ),
            "P1",
            "reg,negative,validation,facebook",
            "After Submit without a password the registration form is still shown and the Password field is visible.",
            testdata(
                "",
                "",
                "Jordan",
                "Lee",
                "",
                "",
                "",
                "",
                "jordan.lee.fb.reg05@example.com",
                "",
                "",
            ),
        ),
        (
            "TC_FB_REG_06",
            "Select date of birth and gender values",
            "No login required. Public registration page at /reg/.",
            steps(
                "1. Open the registration page at /reg/",
                "2. Confirm the text Get started on Facebook is visible",
                "3. Confirm the text Date of birth is visible",
                "4. Confirm the text Gender is visible",
                "5. Select 5 from the Day dropdown",
                "6. Select Dec from the Month dropdown",
                "7. Select 2000 from the Year dropdown",
                "8. Confirm 5 is the selected value",
                "9. Select Female from the Select your gender dropdown",
                "10. Confirm Female is the selected value",
            ),
            steps(
                "1. Registration page is shown",
                "2. Heading Get started on Facebook is visible",
                "3. Date of birth label is visible",
                "4. Gender label is visible",
                "5. Day shows 5 as selected",
                "6. Month shows Dec as selected",
                "7. Year shows 2000 as selected",
                "8. Day selected value is 5",
                "9. Gender shows Female as selected",
                "10. Gender selected value is Female",
            ),
            "P2",
            "reg,dropdown,facebook",
            "The registration form shows date of birth and gender controls on screen.",
            "",
        ),
        (
            "TC_FB_REG_07",
            "Name and contact fields accept typed values",
            "No login required. Public registration page at /reg/.",
            steps(
                "1. Open the registration page at /reg/",
                "2. Confirm the text Get started on Facebook is visible",
                "3. Enter in the First name field",
                "4. Enter in the Surname field",
                "5. Enter in the Mobile number or email address field",
                "6. Enter in the Password field",
                "7. Confirm the First name field is visible",
                "8. Confirm the Surname field is visible",
                "9. Confirm the Mobile number or email address field is visible",
                "10. Confirm the Password field is visible",
            ),
            steps(
                "1. Registration page is shown",
                "2. Heading Get started on Facebook is visible",
                "3. First name accepts the entered value",
                "4. Surname accepts the entered value",
                "5. Email accepts the entered address",
                "6. Password accepts the entered password",
                "7. First name field remains visible",
                "8. Surname field remains visible",
                "9. Mobile number or email address field remains visible",
                "10. Password field remains visible",
            ),
            "P2",
            "reg,fields,facebook",
            "First name, Surname, mobile or email, and Password fields remain visible on the Facebook registration form.",
            testdata(
                "",
                "",
                "Sam",
                "Taylor",
                "sam.taylor.fb.reg07@example.com",
                "SecurePass!98765",
                "",
                "",
                "",
                "",
            ),
        ),
    ]

    for row in rows:
        ws.append(list(row))

    for col, width in zip("ABCDEFGHI", [14, 42, 48, 64, 48, 10, 28, 56, 36]):
        ws.column_dimensions[col].width = width

    OUT.parent.mkdir(parents=True, exist_ok=True)
    wb.save(OUT)
    print(f"wrote {OUT}")
    for row in rows:
        print(f"  {row[0]} — {row[1]}")


if __name__ == "__main__":
    main()
