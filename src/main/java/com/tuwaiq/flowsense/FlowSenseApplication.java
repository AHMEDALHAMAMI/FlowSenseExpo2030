package com.tuwaiq.flowsense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlowSenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowSenseApplication.class, args);
    }
}
