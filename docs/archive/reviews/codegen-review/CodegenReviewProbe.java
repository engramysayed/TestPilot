package delivery.codegen;

import delivery.ir.TcIdentity;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.URLClassLoader;
import java.lang.reflect.Proxy;
import javax.tools.ToolProvider;
import org.openqa.selenium.*;

/** Review-only characterization: a reproduced defect is evidence, not a passing acceptance gate. */
public class CodegenReviewProbe {
    static final Path ROOT = Path.of("target/codegen-review/evidence");
    static final List<String> results = new ArrayList<>();
    static ProvenStep step(String id, String page, String action, String locator, String value, String assertion, String expected) {
        return new ProvenStep(id,page,"elementAction",action,"id",locator,value,assertion,expected,true,"");
    }
    static TcOutcome outcome(String id, ProvenStep... steps) {
        return new TcOutcome(id,"Review fixture",TcStatus.PASSED,List.of(steps),"",null,false,List.of());
    }
    static Path emit(String name, TcOutcome... cases) throws Exception {
        Path dir=ROOT.resolve(name); Files.createDirectories(dir);
        new CodeWriter(Path.of("customer-framework-template/templates")).write(dir,List.of(cases));
        return dir;
    }
    static void record(String id, boolean reproduced, String detail) {
        results.add(id+": "+(reproduced?"REPRODUCED":"NOT REPRODUCED")+" — "+detail);
    }
    public static void main(String[] args) throws Exception {
        Files.createDirectories(ROOT);
        var first=step("TC_EDIT","Profile","type","email","first@example.invalid","","");
        var second=step("TC_EDIT","Profile","type","email","second@example.invalid","","");
        Path edit=emit("repeated-value",outcome("TC_EDIT",first,second));
        String data=Files.readString(edit.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        record("CG01",data.contains("first@example.invalid")&&!data.contains("second@example.invalid"),"second field edit reuses first value");

        var a=step("TC_A_B","PageOne","click","one","","","");
        var b=step("TC_A__B","PageOne","click","two","","","");
        Path ids=emit("case-id-collision",outcome(a.tcId(),a),outcome(b.tcId(),b));
        long files;
        try(var list=Files.list(ids.resolve("src/test/java/project/tests/generated"))) { files=list.count(); }
        record("CG02",TcIdentity.isValid(a.tcId())&&TcIdentity.isValid(b.tcId())&&files==1,"two valid distinct IDs emit "+files+" test class");

        var red=step("TC_ASSERT","Cart","assert","country","","selected","France");
        var blue=step("TC_ASSERT","Cart","assert","country","","selected","Germany");
        var acc=new PageAccumulator(); acc.addAll(List.of(red,blue));
        CodegenSmellCheck.verify(acc.pages());
        record("CG03",acc.pages().get("Cart").assertions().size()==1,"two different selected-option expectations collapse into first assertion");
        emit("assertion-collision",outcome("TC_ASSERT",red,blue));

        var one=step("TC_TYPE","Profile","type","first-name","Alice","","");
        var two=step("TC_TYPE","Profile","type","first_name","Bob","","");
        acc=new PageAccumulator(); acc.addAll(List.of(one,two)); CodegenSmellCheck.verify(acc.pages());
        record("CG04",acc.pages().get("Profile").fields().size()==2&&acc.pages().get("Profile").methods().size()==1,"different fields retained but second action method discarded");
        emit("action-collision",outcome("TC_TYPE",one,two));

        var promo=step("TC_SUBMIT","Cart","click","promo","","","");
        var submit=new ProvenStep("TC_SUBMIT","Cart","elementAction","click","css","button[type='submit']","","","",true,"");
        acc=new PageAccumulator(); acc.addAll(List.of(promo,submit));
        record("CG05",acc.pages().get("Cart").fields().size()==1,"unrelated promo button ID merges with submit selector; referenced method can disappear");
        emit("submit-merge",outcome("TC_SUBMIT",promo,submit));

        Path unicode=emit("unicode",outcome("TC_UNICODE",step("TC_UNICODE","Profile","type","name","مرحبا café","","")));
        Properties p=new Properties(); try(var in=Files.newInputStream(unicode.resolve("src/test/resources/test-data/delivery-testdata.properties"))) { p.load(in); }
        record("CG06",!"مرحبا café".equals(p.getProperty("TC_UNICODE.type_Name")),"UTF-8 data read through Properties.load(InputStream) changes Arabic/accented values");

        Path noLocator=emit("body-text",outcome("TC_BODY",step("TC_BODY","Cart","assert","","","textContains","Saved")));
        String bodyTest=Files.readString(noLocator.resolve("src/test/java/project/tests/generated/TC_BODY.java"));
        record("CG07",bodyTest.contains("assert_Saved_Is_Visible")&&!Files.exists(noLocator.resolve("src/main/java/project/pages/Cart_Actions.java")),"locator-free text assertion call emitted without its page/method");

        Path pages=emit("page-collision",outcome("TC_PAGES",step("TC_PAGES","Order-Details","click","first","","",""),step("TC_PAGES","OrderDetails","click","second","","","")));
        String actions=Files.readString(pages.resolve("src/main/java/project/pages/OrderDetails_Actions.java"));
        record("CG08",!actions.contains("click_First_Button")&&actions.contains("click_Second_Button"),"different page keys sanitize to same output file; last page overwrites first");

        Path classes=ROOT.resolve("template-classes");Files.createDirectories(classes);
        var compiler=ToolProvider.getSystemJavaCompiler();
        try(var fm=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)) {
            var units=fm.getJavaFileObjectsFromPaths(List.of(
                Path.of("customer-framework-template/src/main/java/project/utils/Actions/ElementsHandler.java"),
                Path.of("customer-framework-template/src/main/java/project/validations/Validation.java")));
            boolean ok=compiler.getTask(null,fm,null,List.of("-classpath",System.getProperty("java.class.path"),"-sourcepath","customer-framework-template/src/main/java","-d",classes.toString()),null,units).call();
            if(!ok) throw new IllegalStateException("template helper compilation failed");
        }
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},CodegenReviewProbe.class.getClassLoader())) {
            WebDriver dead=(WebDriver)Proxy.newProxyInstance(CodegenReviewProbe.class.getClassLoader(),new Class[]{WebDriver.class,JavascriptExecutor.class},(proxy,m,x)->{throw new NoSuchSessionException("REVIEW_DEAD_SESSION");});
            System.setProperty("DEFAULT_WAIT","0");
            Class<?> handler=loader.loadClass("project.utils.Actions.ElementsHandler");
            Object h=handler.getConstructor(WebDriver.class).newInstance(dead);
            handler.getMethod("click",By.class).invoke(h,By.id("save"));
            handler.getMethod("type",By.class,String.class).invoke(h,By.id("email"),"review@example.invalid");
            Class<?> validation=loader.loadClass("project.validations.Validation");
            validation.getMethod("assertAll").invoke(null);
            record("CG09",true,"real template click/type returned normally after dead-session errors; assertAll also passed");
            Object v=validation.getConstructor(WebDriver.class).newInstance(dead);
            validation.getMethod("elementNotVisible",By.class).invoke(v,By.id("error"));
            validation.getMethod("assertAll").invoke(null);
            record("CG10",true,"real template notVisible passed for dead WebDriver session");
        }
        Path seed=ROOT.resolve("packager-seed");
        Files.createDirectories(seed.resolve("test-output/Logs"));
        Files.writeString(seed.resolve("test-output/Logs/previous-run.log"),"REVIEW_OLD_RUN_SENTINEL");
        var packager=new delivery.packager.FrameworkPackager();
        Path packed=ROOT.resolve("packager-copy");
        packager.copyTemplate(seed,packed);
        Path zip=ROOT.resolve("packager.zip"); packager.zip(packed,zip);
        try(var z=new java.util.zip.ZipFile(zip.toFile())) {
            record("CG11",z.getEntry("test-output/Logs/previous-run.log")!=null,"prior template execution log copied into customer ZIP");
        }
        Path spaces=emit("whitespace",outcome("TC_SPACES",step("TC_SPACES","Profile","type","name","  leading\rnext","","")));
        Properties spaced=new Properties();
        try(var in=Files.newInputStream(spaces.resolve("src/test/resources/test-data/delivery-testdata.properties"))) {spaced.load(in);}
        record("CG12",!"  leading\rnext".equals(spaced.getProperty("TC_SPACES.type_Name")),"leading spaces and carriage return do not round-trip through generated properties");
        Files.write(ROOT.resolve("results.txt"),results,StandardCharsets.UTF_8);
        results.forEach(System.out::println);
    }
}
