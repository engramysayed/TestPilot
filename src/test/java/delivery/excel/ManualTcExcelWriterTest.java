package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ManualTcExcelWriterTest {

    @Test
    public void write_roundTripsThroughReader() throws Exception {
        ManualTestCase tc = new ManualTestCase(
                "TC_01",
                "Login",
                "1. Open login",
                "1. Page shown",
                "Login required.",
                "P1",
                "smoke",
                "",
                "",
                "EXECUTE"
        );
        Path excel = Files.createTempFile("keel-tc-write-", ".xlsx");
        try {
            ManualTcExcelWriter.write(excel, List.of(tc));
            List<ManualTestCase> read = new ExcelTcReader().read(excel);
            Assert.assertEquals(read.size(), 1);
            Assert.assertEquals(read.get(0).tcId(), "TC_01");
            Assert.assertEquals(read.get(0).keelPath(), "EXECUTE");
        } finally {
            Files.deleteIfExists(excel);
        }
    }
}
