package project.validations;

import org.openqa.selenium.WebDriver;
import org.testng.asserts.SoftAssert;
import project.utils.Logs.LogsManager;

public class Validation extends Assertion{
    private static SoftAssert softAssert=new SoftAssert();
    private static boolean used=false;


    public Validation(WebDriver driver){
        super(driver);
    }

    @Override
    protected void assertTrue(boolean condition, String message) {
        softAssert.assertTrue(condition,message);
        used=true;
    }

    @Override
    protected void assertFalse(boolean condition, String message) {
        softAssert.assertFalse(condition,message);
        used=true;
    }

    @Override
    protected void assertEquals(String actual, String expected, String message) {
        softAssert.assertEquals(actual,expected,message);
        used=true;
    }

    public static void assertAll(){
        if(!used)return;
        try{
            softAssert.assertAll();
        } catch (AssertionError e) {
            LogsManager.error("Assertion Failed "+e.getMessage());
        }
        finally {
            SoftAssert softAssert=new SoftAssert();
        }

    }

}
