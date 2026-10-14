<#--
  Explicit TODO/blocked stub — enabled=false; coverage gate still requires the class.
  Same thin shape as GeneratedMobileTest (shared discipline; avoid FTL drift).
-->
package project.tests.todo;

import org.testng.annotations.Test;
import project.tests.BaseMobileTest;

/**
 * TODO / unproven: ${tcId}.
 * Reason: ${todoReason!\"unproven\"}
 * STOPPED — do not treat an all-TODO suite as successful proof.
 */
public class ${tcId}Todo extends BaseMobileTest {

    @Test(enabled = false, description = \"TODO: ${todoReason!\"unproven\"}\")
    public void ${tcId?lower_case}_todo() {
        // intentionally empty — regenerate when proven
    }
}
