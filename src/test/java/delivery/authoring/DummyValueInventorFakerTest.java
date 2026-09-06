package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Invent() still supplies heal fillers; fromStepOrInvent must not invent TestData.
 */
public class DummyValueInventorFakerTest {

    @Test
    public void inventsFirstAndLastNameLikeWords() {
        String first = DummyValueInventor.invent("input", "text", "firstName", "First name", "");
        String last = DummyValueInventor.invent("input", "text", "lastName", "Last name", "");
        Assert.assertTrue(first.matches("[A-Za-z][A-Za-z'\\- ]{1,40}"), first);
        Assert.assertTrue(last.matches("[A-Za-z][A-Za-z'\\- ]{1,40}"), last);
        Assert.assertNotEquals(first, "Test");
        Assert.assertNotEquals(last, "User");
    }

    @Test
    public void inventsStreetAddress() {
        String address = DummyValueInventor.invent("input", "text", "street", "Street address", "");
        Assert.assertTrue(address.length() >= 5, address);
        Assert.assertTrue(address.matches(".*\\d.*"), "address should include a number: " + address);
    }

    @Test
    public void phoneDigitsFollowMaxLength() {
        String phone = DummyValueInventor.invent(
                "input", "tel", "phone", "Mobile", "", "11", "");
        Assert.assertTrue(phone.matches("\\d{11}"), phone);
    }

    @Test
    public void phoneDigitsFollowCountryHint() {
        String eg = DummyValueInventor.invent(
                "input", "tel", "phone", "Mobile", "", "", "Egypt");
        Assert.assertTrue(eg.matches("\\d{11}"), eg);
        String us = DummyValueInventor.invent(
                "input", "tel", "phone", "Phone", "", "", "United States");
        Assert.assertTrue(us.matches("\\d{10}"), us);
    }

    @Test
    public void explicitExcelValueStillWins() {
        Assert.assertEquals(
                DummyValueInventor.fromStepOrInvent(
                        "Enter Alice in the First name field",
                        "input", "text", "firstName", "First name", ""),
                "Alice");
    }

    @Test
    public void leavesBlankWhenExcelGivesNoConcreteValue() {
        String first = DummyValueInventor.fromStepOrInvent(
                "Enter a first name in the First name field",
                "input", "text", "firstName", "First name", "");
        Assert.assertEquals(first, "");
    }

    @Test
    public void leavesBlankWhenExcelValueLooksLikeAPlaceholderToken() {
        String first = DummyValueInventor.fromStepOrInvent(
                "Enter fname in the First name field",
                "input", "text", "fname", "First name", "");
        Assert.assertEquals(first, "");
        String email = DummyValueInventor.fromStepOrInvent(
                "Enter email in the Email field",
                "input", "email", "email", "Email", "");
        Assert.assertEquals(email, "");
    }

    @Test
    public void testAndUserArePlaceholdersNotTypedData() {
        String first = DummyValueInventor.fromStepOrInvent(
                "Enter Test in the First name field",
                "input", "text", "firstName", "First name", "");
        Assert.assertEquals(first, "");
        String last = DummyValueInventor.fromStepOrInvent(
                "Enter User in the Surname field",
                "input", "text", "lastName", "Surname", "");
        Assert.assertEquals(last, "");
    }

    @Test
    public void columnValueWinsWhenStepHasNoLiteral() {
        String first = DummyValueInventor.fromStepOrInvent(
                "Enter in the First name field", "Jordan",
                "input", "text", "firstName", "First name", "");
        Assert.assertEquals(first, "Jordan");
        String blank = DummyValueInventor.fromStepOrInvent(
                "Enter in the First name field", "",
                "input", "text", "firstName", "First name", "");
        Assert.assertEquals(blank, "");
    }

    @Test
    public void selectEmbeddedValueWinsOverMisalignedColumn() {
        String year = DummyValueInventor.fromStepOrInvent(
                "Select 1995 from the Year dropdown",
                "nora.bennett.reg02@example.com",
                "select", "text", "year", "Year", "");
        Assert.assertEquals(year, "1995");
    }

    @Test
    public void passwordPlaceholderUsesTargetTokenNotInvent() {
        String password = DummyValueInventor.fromStepOrInvent(
                "Enter in the Password field", "<VALID_PASSWORD>",
                "input", "password", "pass", "Password", "");
        Assert.assertEquals(password, "${TARGET_PASSWORD}");
    }

    @Test
    public void usernameHintUsesTargetToken() {
        String user = DummyValueInventor.fromStepOrInvent(
                "Enter in the Username field", "",
                "input", "text", "username", "Username", "");
        Assert.assertEquals(user, "${TARGET_USERNAME}");
    }
}
