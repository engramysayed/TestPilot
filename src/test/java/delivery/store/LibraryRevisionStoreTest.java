package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LibraryRevisionStoreTest {

    @Test
    public void staleBaseRevisionCannotOverwriteANewerEdit() throws Exception {
        Path dir = Files.createTempDirectory("lib-rev");
        LibraryRevisionStore store = new LibraryRevisionStore(dir);
        LibraryRevisionStore.Revision first = store.commit(null, "import", "user-a", "one".getBytes(StandardCharsets.UTF_8));
        LibraryRevisionStore.Revision second = store.commit(first.id(), "edit", "user-b", "two".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(store.head().orElseThrow().id(), second.id());
        try {
            store.commit(first.id(), "review", "ai", "stale".getBytes(StandardCharsets.UTF_8));
            Assert.fail("expected conflict");
        } catch (StaleLibraryRevisionException e) {
            Assert.assertEquals(e.headRevisionId(), second.id());
            Assert.assertEquals(e.baseRevisionId(), first.id());
        }
        Assert.assertEquals(Files.readString(store.headBytes()), "two");
        Assert.assertEquals(Files.readString(store.bytes(first.id())), "one",
                "historical run input must survive later edits");
    }

    @Test
    public void restoreCreatesANewRevisionRatherThanMutatingHistory() throws Exception {
        Path dir = Files.createTempDirectory("lib-rev-restore");
        LibraryRevisionStore store = new LibraryRevisionStore(dir);
        LibraryRevisionStore.Revision first = store.commit(null, "import", "user-a", "one".getBytes(StandardCharsets.UTF_8));
        store.commit(first.id(), "edit", "user-b", "two".getBytes(StandardCharsets.UTF_8));
        LibraryRevisionStore.Revision restored = store.restoreAsNew(first.id(), "user-c");
        Assert.assertNotEquals(restored.id(), first.id());
        Assert.assertEquals(Files.readString(store.headBytes()), "one");
        Assert.assertEquals(restored.parentId(), store.list().get(1).id());
    }
}
