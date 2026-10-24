package com.telt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TeltPlatformServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeltPlatformServiceApplication.class, args);
    }
}
