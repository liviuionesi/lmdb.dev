package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.ChatRequestDto;
import dev.lmdb.ai.dto.ChatResponseDto;
import dev.lmdb.ai.model.Conversation;
import dev.lmdb.ai.model.ConversationType;
import dev.lmdb.ai.model.Message;
import dev.lmdb.ai.repository.ConversationRepository;
import dev.lmdb.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The conversational assistant feature: a user's back-and-forth with the AI, persisted as a {@link
 * Conversation} aggregate so history survives a restart. Collaborates with Spring AI's {@link
 * ChatClient} for the model call and {@link ConversationRepository} for persistence.
 */
@Service
@Slf4j
public class ChatAssistantService {

  private static final String SYSTEM_PROMPT =
      """
        You are LMDB's movie assistant. Answer questions about films,
        actors, and viewing recommendations concisely and helpfully. If asked
        about something unrelated to movies, gently steer the conversation
        back to movies.
        """;

  private final ChatClient chatClient;
  private final ConversationRepository conversationRepository;

  /**
   * @param chatClientBuilder builder for the Spring AI {@link ChatClient} used to generate replies
   * @param conversationRepository persistence for {@link Conversation} aggregates
   */
  public ChatAssistantService(
      ChatClient.Builder chatClientBuilder, ConversationRepository conversationRepository) {
    this.chatClient = chatClientBuilder.build();
    this.conversationRepository = conversationRepository;
  }

  /**
   * Continues (or starts) a conversation with one user message and one assistant reply, persisting
   * both.
   *
   * @param request the incoming user message, and optionally an existing conversation to continue
   * @return the assistant's reply and the conversation id it belongs to
   * @throws ResourceNotFoundException if {@code request.conversationId()} doesn't exist or isn't
   *     owned by {@code request.userId()}
   */
  @Transactional
  public ChatResponseDto chat(ChatRequestDto request) {
    Conversation conversation = resolveConversation(request);

    Message userMessage =
        Message.builder().role("user").content(request.message()).timestamp(Instant.now()).build();
    conversation.addMessage(userMessage);

    // Hand the history to the model as typed messages rather than one concatenated "role: content"
    // string. Concatenation would let a user's own message text contain a line break followed by a
    // role prefix, forging turns — including system turns — that the model cannot tell apart from
    // the ones this service actually emitted.
    List<org.springframework.ai.chat.messages.Message> history =
        conversation.getMessages().stream().map(ChatAssistantService::toModelMessage).toList();

    String reply = chatClient.prompt().system(SYSTEM_PROMPT).messages(history).call().content();
    reply = reply == null ? "" : reply;

    Message assistantMessage =
        Message.builder().role("assistant").content(reply).timestamp(Instant.now()).build();
    conversation.addMessage(assistantMessage);

    conversationRepository.save(conversation);

    return new ChatResponseDto(conversation.getId(), reply);
  }

  /**
   * Translates one persisted turn into the Spring AI message type that carries its role
   * structurally, so the role travels as metadata the caller cannot spoof rather than as text
   * inside the prompt.
   *
   * @param message a persisted conversation turn
   * @return an {@link AssistantMessage} for the assistant's own turns, a {@link UserMessage}
   *     otherwise — an unrecognised role is treated as untrusted user input, never as a system
   *     instruction
   */
  private static org.springframework.ai.chat.messages.Message toModelMessage(Message message) {
    String content = message.getContent() == null ? "" : message.getContent();
    return "assistant".equalsIgnoreCase(message.getRole())
        ? new AssistantMessage(content)
        : new UserMessage(content);
  }

  /**
   * Loads the conversation named by the request, scoped to its owner, or starts a new one when
   * {@code conversationId} is absent.
   *
   * @param request the incoming chat request
   * @return an existing, owned conversation, or a new unsaved one
   * @throws ResourceNotFoundException if {@code request.conversationId()} doesn't exist or isn't
   *     owned by {@code request.userId()}
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
    return conversationRepository
        .findByIdAndUserId(request.conversationId(), request.userId())
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Conversation not found: " + request.conversationId()));
  }
}
