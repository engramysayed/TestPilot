package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * A "selected value" assert is native isSelected() except when the control is a custom picker
 * (combobox / listbox / aria-haspopup). Those never report selected=true on the widget itself.
 */
public class SelectedValueCheckTest {

    @Test
    public void nativeSelectAndCheckboxStayOnIsSelected() {
        Assert.assertFalse(SelectedValueCheck.isCustomPicker("select", "", "", ""));
        Assert.assertFalse(SelectedValueCheck.isCustomPicker("option", "", "", ""));
        Assert.assertFalse(SelectedValueCheck.isCustomPicker("input", "", "", "checkbox"));
        Assert.assertFalse(SelectedValueCheck.isCustomPicker("input", "radio", "", "radio"));
        Assert.assertFalse(SelectedValueCheck.isCustomPicker("div", "button", "", ""));
    }

    @Test
    public void comboboxAndHaspopupAreCustomPickers() {
        Assert.assertTrue(SelectedValueCheck.isCustomPicker("div", "combobox", "", ""));
        Assert.assertTrue(SelectedValueCheck.isCustomPicker("div", "listbox", "", ""));
        Assert.assertTrue(SelectedValueCheck.isCustomPicker("button", "", "listbox", ""));
    }

    @Test
    public void customPickerPassesOnVisibleTextEvenWhenNotHtmlSelected() {
        String err = SelectedValueCheck.evaluate(
                true, "5",
                "div", "combobox", "", "",
                false, "5", "", "Select day");
        Assert.assertNull(err, "visible 5 on a combobox must pass without isSelected()");
    }

    @Test
    public void customPickerPassesOnAriaValueText() {
        String err = SelectedValueCheck.evaluate(
                true, "Dec",
                "div", "combobox", "", "",
                false, "", "Dec", "Select month");
        Assert.assertNull(err);
    }

    @Test
    public void ariaLabelIsTheControlNameNotTheSelectedValue() {
        String err = SelectedValueCheck.evaluate(
                true, "5",
                "div", "combobox", "", "",
                false, "", "", "Select day");
        Assert.assertNotNull(err);
        Assert.assertTrue(err.contains("5"), err);
    }

    @Test
    public void checkboxWithExpectedTextStillRequiresIsSelected() {
        String err = SelectedValueCheck.evaluate(
                true, "Female",
                "input", "", "", "radio",
                false, "Female", "", "");
        Assert.assertNotNull(err, "an unselected radio must not pass just because its label is Female");
    }
}
