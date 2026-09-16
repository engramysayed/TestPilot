package project.validations.support;

import org.testng.annotations.Test;
import project.validations.Validation;

/** Nested-suite fixture; filename avoids Surefire *Test discovery. */
public class PassingCustomerAssertionSample {

    @Test
    public void generatedStylePassingAssertion() {
        new Validation(null).softTrue(true, "AUDIT: subsequent passing customer assertion");
    }
}
