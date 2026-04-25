package org.example.agentaiops.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DashScopeModelFactory {

    private final String apiKey;
    private final String model;

    public DashScopeModelFactory(
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.model:qwen-max}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public QwenModelConfig qwenModelConfig() {
        return new QwenModelConfig(model, apiKey != null && !apiKey.isBlank());
    }
}
