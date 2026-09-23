package delivery.codegen;
import delivery.job.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Proxy;
import org.openqa.selenium.*;

public class ImplementationVerificationProbe {
 public static void main(String[] args) throws Exception {
  Path root=Path.of("target/codegen-review/implementation-probe"); Files.createDirectories(root);
  ProvenStep login=new ProvenStep("TC_SETUP","LoginPage","elementAction","type","id","email","login@example.invalid","","",true,"");
  ProvenStep body=new ProvenStep("TC_SETUP","LoginPage","elementAction","type","id","email","changed@example.invalid","","",true,"");
  TcOutcome pre=new TcOutcome("TC_SETUP","Setup",TcStatus.PASSED,List.of(body),"",null,true,List.of(login));
  ProvenStep leafLogin=new ProvenStep("TC_LEAF","LoginPage","elementAction","type","id","email","leaf@example.invalid","","",true,"");
  TcOutcome leaf=new TcOutcome("TC_LEAF","Leaf",TcStatus.PASSED,List.of(),"",null,true,List.of(leafLogin),List.of(body));
  new CodeWriter(Path.of("customer-framework-template/templates")).write(root,List.of(pre,leaf));
  Properties data=new Properties(); try(var r=Files.newBufferedReader(root.resolve("src/test/resources/test-data/delivery-testdata.properties"))) { data.load(r); }
  List<String> results=new ArrayList<>();
  results.add("Prerequisite original login key TC_SETUP.type_Email.1 = "+data.getProperty("TC_SETUP.type_Email.1")+" (expected login@example.invalid)");
  Path classes=root.resolve("helpers");Files.createDirectories(classes);
  ClassLoader loader=CustomerTemplateCompiler.compile(classes,Path.of("customer-framework-template/src/main/java/project/validations/Validation.java"));
  WebElement hidden=element(false), visible=element(true);
  WebDriver driver=(WebDriver)Proxy.newProxyInstance(ImplementationVerificationProbe.class.getClassLoader(),new Class[]{WebDriver.class},(p,m,a)->switch(m.getName()){
   case "findElement" -> hidden;
   case "findElements" -> List.of(hidden,visible);
   case "toString" -> "two-match-review-driver";
   default -> null;
  });
  System.setProperty("NOT_VISIBLE_WAIT_SECONDS","0");
  Class<?> c=loader.loadClass("project.validations.Validation");Object v=c.getConstructor(WebDriver.class).newInstance(driver);
  c.getMethod("elementNotVisible",By.class).invoke(v,By.cssSelector(".error"));
  try {c.getMethod("assertAll").invoke(null);results.add("Absence assertion PASSED with first match hidden and second visible");}
  catch(java.lang.reflect.InvocationTargetException ex){results.add("Absence assertion failed: "+ex.getCause().getClass().getSimpleName());}
  Files.write(root.resolve("results.txt"),results);results.forEach(System.out::println);
 }
 static WebElement element(boolean shown) {
  return (WebElement)Proxy.newProxyInstance(ImplementationVerificationProbe.class.getClassLoader(),new Class[]{WebElement.class},(p,m,a)->switch(m.getName()){
   case "isDisplayed" -> shown;
   case "toString" -> "review-element-"+shown;
   default -> null;
  });
 }
}
