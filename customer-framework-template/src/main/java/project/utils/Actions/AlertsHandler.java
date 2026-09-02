package project.utils.Actions;

import org.openqa.selenium.WebDriver;
import project.utils.Logs.LogsManager;
import project.utils.WaitHandler;

public class AlertsHandler {
    private final WebDriver driver;
    private WaitHandler waitHandler;
    public AlertsHandler(WebDriver driver){
        this.driver=driver;
        this.waitHandler=new WaitHandler(driver);
    }


    public void acceptAlert(){
        try {
            waitHandler.waitForAlert();
            driver.switchTo().alert().accept();
            LogsManager.info("Alert Accepted");
        } catch (Exception e) {
             LogsManager.error("Alert not found. "+e.getMessage());

        }
    }
    public void dismissAlert(){
        try {
            waitHandler.waitForAlert();
            driver.switchTo().alert().dismiss();
            LogsManager.info("Alert Dismissed");
        } catch (Exception e) {
            LogsManager.error("Alert not found. "+e.getMessage());
        }
    }
    public void sendKeysToAlert(String keys) {
        try {
            waitHandler.waitForAlert();
            driver.switchTo().alert().sendKeys(keys);
            LogsManager.info("Send Keys To Alert.. "+keys);
        } catch (Exception e) {
            LogsManager.error("Alert not found. "+e.getMessage());
        }
    }

    public String getAlertText() {
        try {
            waitHandler.waitForAlert();
            String text = (driver.switchTo().alert().getText());
            LogsManager.info("Alert Text.. "+text);
            return !(text.isEmpty()) ?  text : null;
        } catch (Exception e) {
            LogsManager.error("Alert has no text. "+e.getMessage());
            return null;
        }
    }

}
