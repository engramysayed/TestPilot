package delivery.store;

import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TcDiffService {
    public record DiffResult(List<ManualTestCase> toAuthor, Set<String> unchangedIds) {
    }

    public DiffResult diff(List<ManualTestCase> incoming, Map<String, String> storedHashes) {
        Map<String, String> stored = storedHashes == null ? Map.of() : storedHashes;
        List<ManualTestCase> toAuthor = new ArrayList<>();
        Set<String> unchanged = new HashSet<>();
        for (ManualTestCase tc : incoming) {
            String prev = stored.get(tc.tcId());
            if (prev != null && prev.equals(tc.contentHash())) {
                unchanged.add(tc.tcId());
            } else {
                toAuthor.add(tc);
            }
        }
        return new DiffResult(toAuthor, unchanged);
    }

    public Map<String, String> hashesOf(List<ManualTestCase> cases) {
        Map<String, String> map = new HashMap<>();
        for (ManualTestCase tc : cases) {
            map.put(tc.tcId(), tc.contentHash());
        }
        return map;
    }
}
