package com.example.targetservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TargetServiceApplication {

    /** Starts the target service used by the repair demo. */
    public static void main(String[] args) {
        SpringApplication.run(TargetServiceApplication.class, args);
    }
}
