from openpyxl import Workbook
from pathlib import Path

out = Path(r"D:\priv\testpilot\TestPilot\src\test\resources\delivery\any-site-hard-ladder.xlsx")
wb = Workbook()
ws = wb.active
ws.title = "ManualTCs"
ws.append(["TC_ID", "Title", "Preconditions", "Steps", "ExpectedResult", "Priority", "Tags"])

rows = [
    (
        "TC_HARD_01",
        "Valid login reaches authenticated area",
        "User has valid TARGET credentials for this app. Replace [[...]] tokens before upload.",
        "1. Open the application login page\n"
        "2. Enter the username [[VALID_USERNAME]]\n"
        "3. Enter the password [[VALID_PASSWORD]]\n"
        "4. Click the Login button\n"
        "5. Confirm [[AUTH_LANDMARK_TEXT]] is visible",
        "User is authenticated and [[AUTH_LANDMARK_TEXT]] is shown",
        "P0",
        "login,baseline",
    ),
    (
        "TC_HARD_02",
        "Invalid password stays on login with error",
        "Do not use job TARGET password for the wrong-password step. Honesty: must NOT reach authenticated area.",
        "1. Open the application login page\n"
        "2. Enter the username [[VALID_USERNAME]]\n"
        "3. Enter the password [[WRONG_PASSWORD]]\n"
        "4. Click the Login button\n"
        "5. Confirm [[LOGIN_ERROR_TEXT]] is visible",
        "Login fails and [[LOGIN_ERROR_TEXT]] is shown; authenticated content is not shown",
        "P0",
        "login,negative,honesty",
    ),
    (
        "TC_HARD_03",
        "Navigate to a named section and assert landmark",
        "Provide TARGET credentials on the job if the app gates this page.",
        "1. Open [[SECTION_ENTRY_PATH_OR_LINK_TEXT]]\n"
        "2. Click [[SECTION_LINK_OR_BUTTON]]\n"
        "3. Confirm [[SECTION_LANDMARK_TEXT]] is visible",
        "[[SECTION_LANDMARK_TEXT]] is shown on the section page",
        "P1",
        "nav,assert",
    ),
    (
        "TC_HARD_04",
        "Two distinctly named items — no silent swap",
        "Replace [[ITEM_A]] and [[ITEM_B]] with two DIFFERENT visible names on the same page.",
        "1. Click [[ITEM_A_ACTION]] [[ITEM_A]]\n"
        "2. Click [[ITEM_B_ACTION]] [[ITEM_B]]\n"
        "3. Confirm [[ITEM_A]] is visible\n"
        "4. Confirm [[ITEM_B]] is visible",
        "Both [[ITEM_A]] and [[ITEM_B]] are present; neither was swapped for a third control",
        "P0",
        "binder,distinctive-tokens",
    ),
    (
        "TC_HARD_05",
        "Multi-field form submit with success text",
        "Page with a form requiring at least three fields.",
        "1. Enter [[FIELD_1_LABEL]] [[FIELD_1_VALUE]]\n"
        "2. Enter [[FIELD_2_LABEL]] [[FIELD_2_VALUE]]\n"
        "3. Enter [[FIELD_3_LABEL]] [[FIELD_3_VALUE]]\n"
        "4. Click [[FORM_SUBMIT_BUTTON]]\n"
        "5. Confirm [[FORM_SUCCESS_TEXT]] is visible",
        "Form submits and [[FORM_SUCCESS_TEXT]] is shown",
        "P1",
        "form,type,assert",
    ),
    (
        "TC_HARD_06",
        "Action then control disappears (notVisible)",
        "Choose a control removed/hidden after an action (delete, dismiss, remove).",
        "1. Confirm [[DISAPPEAR_CONTROL_NAME]] is visible\n"
        "2. Click [[DISAPPEAR_TRIGGER_BUTTON]]\n"
        "3. Confirm [[DISAPPEAR_CONTROL_NAME]] is not visible",
        "[[DISAPPEAR_CONTROL_NAME]] is no longer visible after the action",
        "P1",
        "notVisible,state",
    ),
    (
        "TC_HARD_07",
        "Long flow: login, two named actions, review, finish",
        "Full ceiling case. Keep names concrete from the live UI.",
        "1. Open the application login page\n"
        "2. Enter the username [[VALID_USERNAME]]\n"
        "3. Enter the password [[VALID_PASSWORD]]\n"
        "4. Click the Login button\n"
        "5. Confirm [[AUTH_LANDMARK_TEXT]] is visible\n"
        "6. Click [[ITEM_A_ACTION]] [[ITEM_A]]\n"
        "7. Click [[ITEM_B_ACTION]] [[ITEM_B]]\n"
        "8. Click [[REVIEW_OR_CART_OR_NEXT]]\n"
        "9. Confirm [[ITEM_A]] is visible\n"
        "10. Confirm [[ITEM_B]] is visible\n"
        "11. Click [[REMOVE_OR_UNDO]] [[ITEM_B]]\n"
        "12. Click [[CONTINUE_OR_NEXT]]\n"
        "13. Enter [[FIELD_1_LABEL]] [[FIELD_1_VALUE]]\n"
        "14. Enter [[FIELD_2_LABEL]] [[FIELD_2_VALUE]]\n"
        "15. Enter [[FIELD_3_LABEL]] [[FIELD_3_VALUE]]\n"
        "16. Click [[FORM_SUBMIT_BUTTON]]\n"
        "17. Click [[FINISH_OR_CONFIRM_BUTTON]]\n"
        "18. Confirm [[FLOW_SUCCESS_TEXT]] is visible",
        "End-to-end flow completes and [[FLOW_SUCCESS_TEXT]] is shown",
        "P0",
        "e2e,ceiling,multi-page",
    ),
    (
        "TC_HARD_08",
        "Ambiguous near-tie wording (heal stress)",
        "Intentionally weak wording. Expect heal escalate or honest TODO — not a silent wrong bind.",
        "1. Click the main button\n"
        "2. Confirm the page is shown",
        "Primary action ran and a landmark is visible — or TODO/PARTIAL with heal reason",
        "P2",
        "heal,ambiguous",
    ),
]

for r in rows:
    ws.append(list(r))

ws.column_dimensions["A"].width = 14
ws.column_dimensions["B"].width = 48
ws.column_dimensions["C"].width = 42
ws.column_dimensions["D"].width = 72
ws.column_dimensions["E"].width = 52
ws.column_dimensions["F"].width = 10
ws.column_dimensions["G"].width = 28

wb.save(out)
print(f"wrote {out} ({out.stat().st_size} bytes)")
