package delivery.portal.service;

import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.nio.file.*;
import java.util.*;

public class HealthLibraryDependencyTest {
    @Test public void prerequisiteDeletionRejectsWithoutChangingTheRevisionOrWorkbook() throws Exception {
        var props=new DeliveryPortalProperties();props.setStoreRoot(Files.createTempDirectory("health-library").toString());
        var service=new GeneratedWorkbookService(props);
        var setup=new ManualTestCase("TC_SETUP","Setup","","Click add","Added","P1","");
        var middle=new ManualTestCase("TC_MIDDLE","Middle","","Click edit","Edited","P1","","","","","TC_SETUP");
        var leaf=new ManualTestCase("TC_LEAF","Leaf","","Click save","Saved","P1","","","","","TC_MIDDLE");
        var independent=new ManualTestCase("TC_OTHER","Other","","Click other","Other","P1","");
        service.saveFromCases("project",List.of(setup,middle,leaf,independent),"IMPORT","test");
        String head=service.headRevisionId("project").orElseThrow();
        byte[] workbook=Files.readAllBytes(service.generatedDir("project").resolve("latest.xlsx"));
        var error=Assert.expectThrows(IllegalArgumentException.class,()->service.deleteCases("project",List.of("TC_SETUP")));
        Assert.assertTrue(error.getMessage().contains("TC_LEAF"));
        Assert.assertEquals(service.headRevisionId("project").orElseThrow(),head);
        Assert.assertEquals(Files.readAllBytes(service.generatedDir("project").resolve("latest.xlsx")),workbook);
        service.deleteCases("project",List.of("TC_SETUP","TC_MIDDLE","TC_LEAF"));
        Assert.assertEquals(service.readCases("project").stream().map(ManualTestCase::tcId).toList(),List.of("TC_OTHER"));
    }
}
