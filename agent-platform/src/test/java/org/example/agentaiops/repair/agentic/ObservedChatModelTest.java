package org.example.agentaiops.repair.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ObservedChatModelTest {

    @Test
    void recordsTokenUsageFromDelegateChatResponse() {
        AgenticRepairState state =
                new AgenticRepairState("session-usage", Instant.parse("2026-04-29T08:00:00Z"));
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .modelName("model-response")
                .tokenUsage(new TokenUsage(11, 7, 18))
                .build();
        ObservedChatModel model = new ObservedChatModel(
                new FixedResponseChatModel(response),
                state,
                "diagnoseRootCause",
                "DIAGNOSIS",
                "model-configured");

        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("diagnose"))
                .build());

        var timing = state.timing();
        assertThat(timing.modelUsage()).hasSize(1);
        assertThat(timing.modelUsage().get(0).stepName()).isEqualTo("diagnoseRootCause");
        assertThat(timing.modelUsage().get(0).role()).isEqualTo("DIAGNOSIS");
        assertThat(timing.modelUsage().get(0).configuredModel()).isEqualTo("model-configured");
        assertThat(timing.modelUsage().get(0).responseModel()).isEqualTo("model-response");
        assertThat(timing.modelUsage().get(0).inputTokenCount()).isEqualTo(11);
        assertThat(timing.modelUsage().get(0).outputTokenCount()).isEqualTo(7);
        assertThat(timing.modelUsage().get(0).totalTokenCount()).isEqualTo(18);
    }

    private record FixedResponseChatModel(ChatResponse response) implements ChatModel {

        @Override
        public ChatResponse chat(ChatRequest request) {
            return response;
        }
    }
}
