package project.validations.support;

import org.testng.annotations.Test;
import project.validations.Validation;

/** Nested-suite fixture; filename avoids Surefire *Test discovery. */
public class FalseCustomerAssertionSample {

    @Test
    public void generatedStyleFalseAssertion() {
        new Validation(null).softTrue(false, "AUDIT: deliberately false customer assertion");
    }
}
