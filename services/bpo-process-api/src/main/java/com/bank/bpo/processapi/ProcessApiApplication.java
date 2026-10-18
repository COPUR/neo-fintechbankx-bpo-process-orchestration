package com.bank.bpo.processapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** bpo-process-api: the staff task API behind the staff web (client svc-bpo-process). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ProcessApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessApiApplication.class, args);
    }
}
