package project.utils.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test (method or whole class) as read-only: re-running it cannot change application
 * state, so {@link KeelRetryAnalyzer} may retry it. Keel codegen emits this only when the test
 * body and its non-login setup contain no click/type/select/drag step (assertions only). You can
 * add it by hand, or use the TestNG group {@value #GROUP}, to opt a case in.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface KeelReadOnly {
    String GROUP = "keel-readonly";
}
