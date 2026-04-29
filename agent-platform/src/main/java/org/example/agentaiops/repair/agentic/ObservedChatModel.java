package org.example.agentaiops.repair.agentic;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;

/** Records model usage at the ChatModel boundary, where token metadata is still available. */
final class ObservedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final AgenticRepairState state;
    private final String stepName;
    private final String role;
    private final String configuredModel;

    ObservedChatModel(
            ChatModel delegate,
            AgenticRepairState state,
            String stepName,
            String role,
            String configuredModel) {
        this.delegate = delegate;
        this.state = state;
        this.stepName = stepName;
        this.role = role;
        this.configuredModel = configuredModel;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return record(delegate.chat(request));
    }

    @Override
    public ChatResponse chat(ChatRequest request, ChatRequestOptions options) {
        return record(delegate.chat(request, options));
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return record(delegate.doChat(request));
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private ChatResponse record(ChatResponse response) {
        state.recordModelUsage(stepName, role, configuredModel, response);
        return response;
    }
}
