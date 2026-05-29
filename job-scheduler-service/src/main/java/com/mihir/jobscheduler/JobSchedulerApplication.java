package com.mihir.jobscheduler;

import com.mihir.jobscheduler.config.ChronosRateLimitProperties;
import com.mihir.jobscheduler.config.ChronosSchedulerProperties;
import com.mihir.jobscheduler.config.ChronosSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        ChronosSchedulerProperties.class,
        ChronosSecurityProperties.class,
        ChronosRateLimitProperties.class
})
public class JobSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobSchedulerApplication.class, args);
    }
}
