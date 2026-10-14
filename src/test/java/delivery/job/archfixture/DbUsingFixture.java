package delivery.job.archfixture;

/** Test-only fixture for ModuleBoundaryArchTest: an engine-side class that uses JDBC and a portal class. */
public final class DbUsingFixture {
    public java.sql.Connection connection;
    public delivery.portal.archfixture.SeleniumUsingFixture portal;
}
