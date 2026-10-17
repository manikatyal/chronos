package com.mihir.jobworker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(WorkerProcessingProperties.class)
public class WorkerExecutionConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService emailExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService notificationExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService newsExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService paymentExecutor() {
        return Executors.newFixedThreadPool(3);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService orderExecutor() {
        return Executors.newFixedThreadPool(3);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService hotelExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService syncExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService reportExecutor() {
        return Executors.newFixedThreadPool(1);
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return builder
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; ChronosWorker/1.0)")
                .build();
    }
}
