from openpyxl import Workbook
from pathlib import Path

# Filled instance of any-site-hard-ladder for https://www.saucedemo.com/
# (tokens replaced with live UI wording — still no SauceDemo code in the engine)

out = Path(r"D:\priv\testpilot\TestPilot\src\test\resources\delivery\hard-ladder-saucedemo-filled.xlsx")
wb = Workbook()
ws = wb.active
ws.title = "ManualTCs"
ws.append(["TC_ID", "Title", "Preconditions", "Steps", "ExpectedResult", "Priority", "Tags"])

rows = [
    (
        "TC_HARD_01",
        "Valid login reaches authenticated area",
        "standard_user / secret_sauce",
        "1. Open the application login page\n"
        "2. Enter the username standard_user\n"
        "3. Enter the password secret_sauce\n"
        "4. Click the Login button\n"
        "5. Confirm Products is visible",
        "User is authenticated and Products is shown",
        "P0",
        "login,baseline",
    ),
    (
        "TC_HARD_02",
        "Invalid password stays on login with error",
        "Wrong password must not authenticate",
        "1. Open the application login page\n"
        "2. Enter the username standard_user\n"
        "3. Enter the password wrong_password\n"
        "4. Click the Login button\n"
        "5. Confirm Username and password do not match is visible",
        "Login fails and error is shown",
        "P0",
        "login,negative,honesty",
    ),
    (
        "TC_HARD_03",
        "Navigate to a named section and assert landmark",
        "Logged-in inventory is the catalog section",
        "1. Open the application login page\n"
        "2. Enter the username standard_user\n"
        "3. Enter the password secret_sauce\n"
        "4. Click the Login button\n"
        "5. Confirm Products is visible\n"
        "6. Click the shopping cart\n"
        "7. Confirm Your Cart is visible",
        "Your Cart is shown on the cart page",
        "P1",
        "nav,assert",
    ),
    (
        "TC_HARD_04",
        "Two distinctly named items — no silent swap",
        "Two different product add buttons on inventory",
        "1. Open the application login page\n"
        "2. Enter the username standard_user\n"
        "3. Enter the password secret_sauce\n"
        "4. Click the Login button\n"
        "5. Confirm Products is visible\n"
        "6. Add Sauce Labs Backpack to the cart\n"
        "7. Add Sauce Labs Bike Light to the cart\n"
        "8. Click the shopping cart\n"
        "9. Confirm Sauce Labs Backpack is visible\n"
        "10. Confirm Sauce Labs Bike Light is visible",
        "Both Backpack and Bike Light are in the cart",
        "P0",
        "binder,distinctive-tokens",
    ),
    (
        "TC_HARD_05",
        "Multi-field form submit with success text",
        "Checkout step one form after adding one item",
        "1. Open the application login page\n"
        "2. Enter the username standard_user\n"
        "3. Enter the password secret_sauce\n"
        "4. Click the Login button\n"
        "5. Confirm Products is visible\n"
        "6. Add Sauce Labs Backpack to the cart\n"
        "7. Click the shopping cart\n"
        "8. Click Checkout\n"
        "9. Enter First Name John\n"
        "10. Enter Last Name Doe\n"
        "11. Enter Postal Code 12345\n"
        "12. Click Continue\n"
        "13. Confirm Checkout: Overview is visible",
        "Checkout overview is shown",
        "P1",
        "form,type,assert",
    ),
    (
        "TC_HARD_06",
        "Action then control disappears (notVisible)",
        "Remove button for an item should go away after remove",
        "1. Open the application login page\n"
        "2. Enter the username standard_user\n"
        "3. Enter the password secret_sauce\n"
        "4. Click the Login button\n"
        "5. Confirm Products is visible\n"
        "6. Add Sauce Labs Bike Light to the cart\n"
        "7. Click the shopping cart\n"
        "8. Confirm Sauce Labs Bike Light is visible\n"
        "9. Remove Sauce Labs Bike Light\n"
        "10. Confirm Remove Sauce Labs Bike Light is not visible",
        "Remove control for Bike Light is gone",
        "P1",
        "notVisible,state",
    ),
    (
        "TC_HARD_07",
        "Long flow: login, two named actions, review, finish",
        "Full ceiling checkout",
        "1. Open the application login page\n"
        "2. Enter the username standard_user\n"
        "3. Enter the password secret_sauce\n"
        "4. Click the Login button\n"
        "5. Confirm Products is visible\n"
        "6. Add Sauce Labs Backpack to the cart\n"
        "7. Add Sauce Labs Bike Light to the cart\n"
        "8. Click the shopping cart\n"
        "9. Confirm Sauce Labs Backpack is visible\n"
        "10. Confirm Sauce Labs Bike Light is visible\n"
        "11. Remove Sauce Labs Bike Light\n"
        "12. Click Checkout\n"
        "13. Enter First Name John\n"
        "14. Enter Last Name Doe\n"
        "15. Enter Postal Code 12345\n"
        "16. Click Continue\n"
        "17. Click Finish\n"
        "18. Confirm Thank you for your order is visible",
        "Order completes with thank you message",
        "P0",
        "e2e,ceiling,multi-page",
    ),
    (
        "TC_HARD_08",
        "Ambiguous near-tie wording (heal stress)",
        "Intentionally weak — expect heal or honest TODO",
        "1. Open the application login page\n"
        "2. Enter the username standard_user\n"
        "3. Enter the password secret_sauce\n"
        "4. Click the Login button\n"
        "5. Click the main button\n"
        "6. Confirm the page is shown",
        "Primary action ran or case is TODO/PARTIAL with heal reason",
        "P2",
        "heal,ambiguous",
    ),
]

for r in rows:
    ws.append(list(r))

for col, w in zip("ABCDEFG", (14, 48, 36, 72, 40, 8, 28)):
    ws.column_dimensions[col].width = w

wb.save(out)
print("wrote", out)
