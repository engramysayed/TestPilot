package delivery.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    // Job executors: each run needs its own thread. corePool=2 keeps two users unblocked;
    // maxPool=4 lets the fourth user wait only briefly before a burst thread picks it up.
    // The old (1,2) pool queued every third run until the current one finished.

    @Bean(name = "conversionExecutor")
    public Executor conversionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("conversion-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "executeExecutor")
    public Executor executeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("execute-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "generateBatchExecutor")
    public Executor generateBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Batch jobs are heavier; keep serial by design but allow a 2nd on standby for a burst.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("generate-batch-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "huntExecutor")
    public Executor huntExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("hunt-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Each member of a project may get a parallel SMTP/HTTPS send; 4 threads handles fan-out
        // for typical team sizes without piling up when Gmail is slow.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("webhook-");
        // Overflow: run the task on the calling thread instead of dropping it silently.
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
