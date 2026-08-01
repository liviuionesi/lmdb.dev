package com.filmpire.ai.service;

import com.filmpire.ai.dto.ChatRequestDto;
import com.filmpire.ai.dto.ChatResponseDto;
import com.filmpire.ai.model.Conversation;
import com.filmpire.ai.model.ConversationType;
import com.filmpire.ai.model.Message;
import com.filmpire.ai.repository.ConversationRepository;
import com.filmpire.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * The conversational assistant feature (#36, ARCHITECTURE.md §3.7): a
 * user's back-and-forth with the AI, persisted as a {@link Conversation}
 * aggregate so history survives a restart (ADR-012's whole point).
 */
@Service
@Slf4j
public class ChatAssistantService {

    private static final String SYSTEM_PROMPT = """
        You are Filmpire's movie assistant. Answer questions about films,
        actors, and viewing recommendations concisely and helpfully. If asked
        about something unrelated to movies, gently steer the conversation
        back to movies.
        """;

    private final ChatClient chatClient;
    private final ConversationRepository conversationRepository;

    public ChatAssistantService(ChatClient.Builder chatClientBuilder, ConversationRepository conversationRepository) {
        this.chatClient = chatClientBuilder.build();
        this.conversationRepository = conversationRepository;
    }

    /**
     * Continues (or starts) a conversation with one user message and one
     * assistant reply, persisting both.
     *
     * @param request the incoming user message, and optionally an existing conversation to continue
     * @return the assistant's reply and the conversation id it belongs to
     * @throws ResourceNotFoundException if {@code request.conversationId()} doesn't exist or isn't owned by {@code request.userId()}
     */
    @Transactional
    public ChatResponseDto chat(ChatRequestDto request) {
        Conversation conversation = resolveConversation(request);

        Message userMessage = Message.builder()
            .role("user")
            .content(request.message())
            .timestamp(Instant.now())
            .build();
        conversation.addMessage(userMessage);

        String history = conversation.getMessages().stream()
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));

        String reply = chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user(history)
            .call()
            .content();
        reply = reply == null ? "" : reply;

        Message assistantMessage = Message.builder()
            .role("assistant")
            .content(reply)
            .timestamp(Instant.now())
            .build();
        conversation.addMessage(assistantMessage);

        conversationRepository.save(conversation);

        return new ChatResponseDto(conversation.getId(), reply);
    }

    /**
     * Loads the conversation named by the request, scoped to its owner, or
     * starts a new one when {@code conversationId} is absent.
     */
    private Conversation resolveConversation(ChatRequestDto request) {
        if (request.conversationId() == null) {
            return Conversation.builder()
                .userId(request.userId())
                .type(ConversationType.CHAT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        }
        return conversationRepository.findByIdAndUserId(request.conversationId(), request.userId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Conversation not found: " + request.conversationId()));
    }
}
