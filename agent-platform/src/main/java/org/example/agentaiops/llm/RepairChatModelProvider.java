package org.example.agentaiops.llm;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.example.agentaiops.config.RepairProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RepairChatModelProvider {

    private final RepairProperties properties;
    private final String openAiApiKey;
    private final String openAiModel;
    private final String openAiBaseUrl;
    private final String dashScopeApiKey;
    private final String dashScopeModel;
    private final String dashScopeBaseUrl;
    private volatile ChatModel chatModel;

    /** Stores model configuration and delays client creation until the first real call. */
    public RepairChatModelProvider(
            RepairProperties properties,
            @Value("${openai.api-key:}") String openAiApiKey,
            @Value("${openai.model:gpt-4o-mini}") String openAiModel,
            @Value("${openai.base-url:https://api.openai.com/v1}") String openAiBaseUrl,
            @Value("${dashscope.api-key:}") String dashScopeApiKey,
            @Value("${dashscope.model:qwen-max}") String dashScopeModel,
            @Value("${dashscope.base-url:}") String dashScopeBaseUrl) {
        this.properties = properties;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.openAiBaseUrl = openAiBaseUrl;
        this.dashScopeApiKey = dashScopeApiKey;
        this.dashScopeModel = dashScopeModel;
        this.dashScopeBaseUrl = dashScopeBaseUrl;
    }

    /** Returns true only when LLM repair is enabled and an API key is configured. */
    public boolean available() {
        if (!properties.getLlm().isEnabled()) {
            return false;
        }
        return switch (provider()) {
            case "openai" -> hasText(openAiApiKey);
            case "dashscope" -> hasText(dashScopeApiKey);
            default -> false;
        };
    }

    /** Exposes the configured model name for diagnostics and repair records. */
    public String modelName() {
        return switch (provider()) {
            case "dashscope" -> dashScopeModel;
            case "openai" -> openAiModel;
            default -> "";
        };
    }

    /** Builds and reuses the configured LangChain4j chat model client. */
    public ChatModel chatModel() {
        if (!available()) {
            throw new IllegalStateException("Repair LLM is disabled or provider API key is not configured");
        }
        ChatModel current = chatModel;
        if (current == null) {
            synchronized (this) {
                current = chatModel;
                if (current == null) {
                    current = switch (provider()) {
                        case "openai" -> buildOpenAiModel();
                        case "dashscope" -> buildDashScopeModel();
                        default -> throw new IllegalStateException(
                                "Unsupported repair.llm.provider: " + properties.getLlm().getProvider());
                    };
                    chatModel = current;
                }
            }
        }
        return current;
    }

    /** Builds the OpenAI chat model used by the real repair Agent. */
    private ChatModel buildOpenAiModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .baseUrl(openAiBaseUrl)
                .modelName(openAiModel)
                .temperature((double) properties.getLlm().getTemperature())
                .maxTokens(properties.getLlm().getMaxTokens())
                .timeout(Duration.ofSeconds(properties.getLlm().getTimeoutSeconds()))
                .maxRetries(properties.getLlm().getMaxRetries())
                .build();
    }

    /** Builds the DashScope/Qwen chat model kept as an optional provider. */
    private ChatModel buildDashScopeModel() {
        QwenChatModel.QwenChatModelBuilder builder = QwenChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(dashScopeModel)
                .temperature(properties.getLlm().getTemperature())
                .maxTokens(properties.getLlm().getMaxTokens());
        if (hasText(dashScopeBaseUrl)) {
            builder.baseUrl(dashScopeBaseUrl);
        }
        return builder.build();
    }

    /** Normalizes the provider name for switch-based selection. */
    private String provider() {
        String provider = properties.getLlm().getProvider();
        return provider == null ? "openai" : provider.trim().toLowerCase();
    }

    /** Checks whether a provider configuration value is present. */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
