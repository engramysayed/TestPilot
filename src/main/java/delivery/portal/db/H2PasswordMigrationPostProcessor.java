package delivery.portal.db;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs {@link H2PasswordMigrator} once, before the pool/JPA first uses the DataSource bean.
 * Disable with {@code keel.db.migrate-empty-password=false}.
 */
@Component
public class H2PasswordMigrationPostProcessor implements BeanPostProcessor {
    private final Environment env;
    private volatile boolean done;

    public H2PasswordMigrationPostProcessor(Environment env) {
        this.env = env;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (!done && bean instanceof DataSource
                && Boolean.parseBoolean(env.getProperty("keel.db.migrate-empty-password", "true"))) {
            done = true;
            H2PasswordMigrator.migrate(env.getProperty("spring.datasource.url"),
                    env.getProperty("spring.datasource.username", "sa"),
                    env.getProperty("spring.datasource.password", ""));
        }
        return bean;
    }
}
