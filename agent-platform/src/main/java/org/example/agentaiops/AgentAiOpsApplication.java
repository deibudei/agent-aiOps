package org.example.agentaiops;

import org.example.agentaiops.config.RepairProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RepairProperties.class)
public class AgentAiOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentAiOpsApplication.class, args);
    }
}
