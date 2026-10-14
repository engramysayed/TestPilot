package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LibraryRevisionStoreTest {
    @Test public void corruptedRestoreDoesNotChangeTheHead() throws Exception {
        Path dir = Files.createTempDirectory("restore-corrupt-");
        LibraryRevisionStore store = new LibraryRevisionStore(dir);
        var first = store.commit(null, "import", "owner", "original".getBytes(StandardCharsets.UTF_8));
        var second = store.commit(first.id(), "edit", "owner", "current".getBytes(StandardCharsets.UTF_8));
        Files.writeString(store.bytes(first.id()), "corrupted");
        try { store.restoreAsNew(first.id(), "owner"); Assert.fail("Expected corrupt revision rejection"); }
        catch (java.io.IOException e) { Assert.assertTrue(e.getMessage().contains("LIBRARY_REVISION_CORRUPT")); }
        Assert.assertEquals(store.head().orElseThrow().id(), second.id());
        Assert.assertEquals(Files.readString(store.headBytes()), "current");
    }

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
