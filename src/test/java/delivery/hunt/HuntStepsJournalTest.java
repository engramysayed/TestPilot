package delivery.hunt;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class HuntStepsJournalTest {
    @Test
    public void typeValuesAreRedactedInJournalAndPrompt() throws Exception {
        var dir = Files.createTempDirectory("hunt-journal-");
        HuntStepsJournal j = new HuntStepsJournal(dir);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "type");
        row.put("locator", "#password");
        row.put("value", "SuperSecretOTP-999");
        row.put("status", "ok");
        j.appendActions(1, List.of(row));
        String body = Files.readString(dir.resolve(HuntStepsJournal.FILE_NAME));
        assertFalse(body.contains("SuperSecretOTP-999"), body);
        assertTrue(body.contains("value=(redacted)"), body);
        assertFalse(j.forPrompt().contains("SuperSecretOTP"), j.forPrompt());
    }
}
