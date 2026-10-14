package delivery.mobile.runner.settle;

import delivery.mobile.runner.snapshot.MobileSnapshot;

/** Supplies fresh hierarchy snapshots during settle (driver / test fake). */
@FunctionalInterface
public interface HierarchyProbe {
    MobileSnapshot capture();
}
