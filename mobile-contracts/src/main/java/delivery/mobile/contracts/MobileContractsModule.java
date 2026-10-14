package delivery.mobile.contracts;

/**
 * Module marker so {@code mobile-contracts} compiles before AE lands IR DTOs (M0-03).
 */
public final class MobileContractsModule {
    public static final String ARTIFACT_ID = "mobile-contracts";
    public static final String SCHEMA_VERSION_PLACEHOLDER = "mobile-ir/1";

    private MobileContractsModule() {
    }
}
