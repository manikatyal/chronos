package com.mihir.jobworker.service;

import com.mihir.jobworker.exception.JobProcessingException;
import com.mihir.jobworker.model.JobName;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class JobExecutorRegistry {

    private static final long JOB_TIMEOUT_SECONDS = 60;

    private final Map<JobName, ExecutorService> executors = new EnumMap<>(JobName.class);

    public JobExecutorRegistry(
            @Qualifier("emailExecutor") ExecutorService emailExecutor,
            @Qualifier("notificationExecutor") ExecutorService notificationExecutor,
            @Qualifier("newsExecutor") ExecutorService newsExecutor,
            @Qualifier("paymentExecutor") ExecutorService paymentExecutor,
            @Qualifier("orderExecutor") ExecutorService orderExecutor,
            @Qualifier("hotelExecutor") ExecutorService hotelExecutor,
            @Qualifier("syncExecutor") ExecutorService syncExecutor,
            @Qualifier("reportExecutor") ExecutorService reportExecutor) {
        executors.put(JobName.SEND_EMAIL, emailExecutor);
        executors.put(JobName.SEND_NOTIFICATION, notificationExecutor);
        executors.put(JobName.FETCH_NEWS_HEADLINES, newsExecutor);
        executors.put(JobName.RETRY_PAYMENT, paymentExecutor);
        executors.put(JobName.ORDER_EXPIRY, orderExecutor);
        executors.put(JobName.HOTEL_NIGHT_AUDIT, hotelExecutor);
        executors.put(JobName.DATA_SYNC, syncExecutor);
        executors.put(JobName.GENERATE_REPORT, reportExecutor);
    }

    public <T> T execute(JobName jobName, Supplier<T> supplier) {
        ExecutorService executor = executors.get(jobName);
        if (executor == null) {
            throw new JobProcessingException("No executor configured for jobName: " + jobName);
        }
        // Bound every job so one hung call cannot block the single Kafka listener thread.
        return CompletableFuture.supplyAsync(supplier, executor)
                .orTimeout(JOB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();
    }
}
