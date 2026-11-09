package delivery.mobile.runner.agent;

import org.testng.Assert;
import org.testng.annotations.Test;

public class MobileStepTest {

    @Test
    public void englishSteps() {
        MobileStep tap = MobileStep.parse("Tap the \"Login\" button", "");
        Assert.assertEquals(tap.kind(), MobileStep.Kind.TAP);
        Assert.assertEquals(tap.target(), "Login");

        MobileStep type = MobileStep.parse("Enter in the Email field", "sara@example.com");
        Assert.assertEquals(type.kind(), MobileStep.Kind.TYPE);
        Assert.assertEquals(type.target(), "email");
        Assert.assertEquals(type.value(), "sara@example.com");

        MobileStep typed = MobileStep.parse("Type \"hello\" into \"Message\"", "");
        Assert.assertEquals(typed.value(), "hello");
        Assert.assertEquals(typed.target(), "Message");

        Assert.assertEquals(MobileStep.parse("Verify \"Welcome\" is displayed", "").kind(), MobileStep.Kind.ASSERT_VISIBLE);
        Assert.assertEquals(MobileStep.parse("Confirm \"Error\" is not visible", "").kind(), MobileStep.Kind.ASSERT_NOT_VISIBLE);
        Assert.assertEquals(MobileStep.parse("Go back", "").kind(), MobileStep.Kind.BACK);
        Assert.assertEquals(MobileStep.parse("Swipe up", "").direction(), "UP");
        Assert.assertEquals(MobileStep.parse("Launch the app", "").kind(), MobileStep.Kind.LAUNCH);
    }

    @Test
    public void wholeWordVerbs() {
        // "center" must not be read as "enter"
        Assert.assertEquals(MobileStep.parse("Tap the center tab", "").kind(), MobileStep.Kind.TAP);
    }

    @Test
    public void arabicSteps() {
        MobileStep tap = MobileStep.parse("اضغط على زر \"دخول\"", "");
        Assert.assertEquals(tap.kind(), MobileStep.Kind.TAP);
        Assert.assertEquals(tap.target(), "دخول");
        Assert.assertEquals(MobileStep.parse("أدخل في حقل البريد", "a@b.com").kind(), MobileStep.Kind.TYPE);
        Assert.assertEquals(MobileStep.parse("تحقق من ظهور \"مرحبا\"", "").kind(), MobileStep.Kind.ASSERT_VISIBLE);
        Assert.assertEquals(MobileStep.parse("ارجع", "").kind(), MobileStep.Kind.BACK);
        Assert.assertEquals(MobileStep.parse("اسحب للأعلى", "").direction(), "UP");
    }

    @Test
    public void normalizesArabicLetterForms() {
        Assert.assertEquals(MobileStep.normalize("أدخل كلمةُ المرور"), MobileStep.normalize("ادخل كلمه المرور"));
    }
}
