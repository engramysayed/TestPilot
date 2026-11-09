package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class IntentTextTest {

    @Test
    public void arabicStepProducesTokensAndStripsArticle() {
        List<String> t = IntentText.tokens("اضغط على زر الحفظ");
        Assert.assertTrue(t.contains("حفظ"), t.toString());
        Assert.assertFalse(t.contains("اضغط"), "verb is a stop word: " + t);
    }

    @Test
    public void normalizesAlefTehMarbutaAndDigits() {
        Assert.assertEquals(IntentText.normalize("إدخال كلمةُ المرور ١٢٣"), "ادخال كلمه المرور 123");
    }

    @Test
    public void wholeWordNotSubstring() {
        Assert.assertFalse(IntentText.containsToken("Address", "add"));
        Assert.assertTrue(IntentText.containsToken("Add to cart", "add"));
        Assert.assertTrue(IntentText.containsToken("button[data-testid='add-to-cart']", "add"));
        Assert.assertTrue(IntentText.containsToken("placeOrderButton", "order"));
        Assert.assertFalse(IntentText.containsToken("Unsaved changes", "save"));
    }

    @Test
    public void arabicWordMatchesWithOrWithoutArticle() {
        Assert.assertTrue(IntentText.containsToken("زر الحفظ", "حفظ"));
        Assert.assertTrue(IntentText.containsToken("حفظ", "حفظ"));
    }

    @Test
    public void arabicVerbsClassify() {
        Assert.assertEquals(StepIntentBinder.classify("اضغط على زر حفظ"), StepIntentBinder.IntentKind.CLICK);
        Assert.assertEquals(StepIntentBinder.classify("ادخل اسم المستخدم"), StepIntentBinder.IntentKind.TYPE_USER);
        Assert.assertEquals(StepIntentBinder.classify("ادخل كلمة المرور"), StepIntentBinder.IntentKind.TYPE_PASS);
        Assert.assertEquals(StepIntentBinder.classify("تحقق من ظهور رسالة النجاح"), StepIntentBinder.IntentKind.ASSERT_VISIBLE);
        Assert.assertEquals(StepIntentBinder.classify("اضغط على تسجيل الدخول"), StepIntentBinder.IntentKind.CLICK_LOGIN);
    }

    @Test
    public void englishUnchanged() {
        Assert.assertEquals(IntentText.annotateArabicVerbs("click the save button"), "click the save button");
    }
}
