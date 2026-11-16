import project.validations.Validation;

/** Runs the freshly compiled customer template, without a browser or customer data. */
public class TemplateAssertionProbe {
    public static void main(String[] args) {
        Validation validation = new Validation(null);
        validation.softTrue(false, "AUDIT: deliberately false customer assertion");
        boolean propagated = false;
        try {
            Validation.assertAll();
        } catch (AssertionError expected) {
            propagated = true;
        }
        System.out.println("F00 | " + (!propagated ? "DEFECT REPRODUCED" : "NOT REPRODUCED")
                + " | Deliberately false customer assertion propagated to runner: " + propagated);
    }
}
