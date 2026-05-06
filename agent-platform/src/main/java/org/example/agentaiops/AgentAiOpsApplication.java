package org.example.agentaiops;

import org.example.agentaiops.config.RepairProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(RepairProperties.class)
@EnableScheduling
public class AgentAiOpsApplication {

    /** Starts the Agent platform Spring Boot application. */
    public static void main(String[] args) {
        SpringApplication.run(AgentAiOpsApplication.class, args);
    }
}
