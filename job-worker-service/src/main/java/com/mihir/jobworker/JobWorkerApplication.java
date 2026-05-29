package com.mihir.jobworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class JobWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobWorkerApplication.class, args);
    }
}
