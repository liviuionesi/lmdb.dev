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
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The conversational assistant feature: a user's back-and-forth with the AI, persisted as a {@link
 * Conversation} aggregate so history survives a restart. Collaborates with Spring AI's {@link
 * ChatClient} for the model call and {@link ConversationRepository} for persistence.
 *
 * <p>{@code chat()} deliberately does not carry one enclosing {@code @Transactional}: the model
 * call inside it has unbounded latency, and holding a pooled DB connection for that long starves
 * every other request the moment concurrency exceeds the pool size. Persistence is instead scoped
 * to two short {@link TransactionTemplate} blocks around just the DB work, with the model call in
 * between running with no transaction open at all. A hand-managed {@link TransactionTemplate} is
 * used rather than {@code @Transactional} on private helper methods because Spring's proxy-based
 * AOP does not intercept a class's calls to its own methods — that would silently no-op here.
 */
@Service
@Slf4j
public class ChatAssistantService {

  /** Most recent turns sent to the model; older history is dropped to bound prompt growth. */
  private static final int HISTORY_WINDOW = 20;

  private static final String SYSTEM_PROMPT =
      """
        You are LMDB's movie assistant. Answer questions about films,
        actors, and viewing recommendations concisely and helpfully. If asked
        about something unrelated to movies, gently steer the conversation
        back to movies.
        """;

  private final ChatClient chatClient;
  private final ConversationRepository conversationRepository;
  private final TransactionTemplate transactionTemplate;

  /**
   * @param chatClientBuilder builder for the Spring AI {@link ChatClient} used to generate replies
   * @param conversationRepository persistence for {@link Conversation} aggregates
   * @param transactionManager backs the short, hand-scoped transactions around persistence — see
   *     the class Javadoc for why {@code @Transactional} isn't used here
   */
  public ChatAssistantService(
      ChatClient.Builder chatClientBuilder,
      ConversationRepository conversationRepository,
      PlatformTransactionManager transactionManager) {
    this.chatClient = chatClientBuilder.build();
    this.conversationRepository = conversationRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
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
  public ChatResponseDto chat(ChatRequestDto request) {
    // 1. Short transaction: resolve the conversation, persist the user's turn immediately (so it
    //    survives even if the model call below times out or fails), and materialize the windowed
    //    history as plain Spring AI messages while the persistence context is still open — this is
    //    what lets a LAZY collection read (Conversation.messages) stay safe on the gRPC transport,
    //    which has no Spring MVC request scope to fall back on.
    ConversationSnapshot snapshot =
        transactionTemplate.execute(status -> loadHistoryAndAppendUserMessage(request));

    // 2. The slow, unbounded-latency step, deliberately outside any transaction: no DB connection
    //    is held from the pool for however long the model takes to reply.
    String reply =
        chatClient.prompt().system(SYSTEM_PROMPT).messages(snapshot.history()).call().content();
    reply = reply == null ? "" : reply;

    // 3. A second short transaction to persist the assistant's reply.
    String finalReply = reply;
    transactionTemplate.executeWithoutResult(
        status -> appendAssistantReply(snapshot.conversationId(), request.userId(), finalReply));

    return new ChatResponseDto(snapshot.conversationId(), reply);
  }

  /**
   * Runs inside {@link #transactionTemplate}: resolves the conversation, appends and persists the
   * user's message, and returns the windowed history ready for the model call.
   *
   * @param request the incoming chat request
   * @return the conversation's id and its history, trimmed to {@link #HISTORY_WINDOW} turns
   * @throws ResourceNotFoundException if {@code request.conversationId()} doesn't exist or isn't
   *     owned by {@code request.userId()}
   */
  private ConversationSnapshot loadHistoryAndAppendUserMessage(ChatRequestDto request) {
    Conversation conversation = resolveConversation(request);

    Message userMessage =
        Message.builder().role("user").content(request.message()).timestamp(Instant.now()).build();
    conversation.addMessage(userMessage);
    conversationRepository.save(conversation);

    // Hand the history to the model as typed messages rather than one concatenated "role: content"
    // string. Concatenation would let a user's own message text contain a line break followed by a
    // role prefix, forging turns — including system turns — that the model cannot tell apart from
    // the ones this service actually emitted.
    List<org.springframework.ai.chat.messages.Message> history =
        conversation.getMessages().stream().map(ChatAssistantService::toModelMessage).toList();
    if (history.size() > HISTORY_WINDOW) {
      history = history.subList(history.size() - HISTORY_WINDOW, history.size());
    }

    return new ConversationSnapshot(conversation.getId(), history);
  }

  /**
   * Runs inside {@link #transactionTemplate}: re-fetches the conversation, scoped to its owner, and
   * appends the assistant's reply.
   *
   * @param conversationId the conversation to append to, resolved (and persisted) in step 1
   * @param userId the owner, re-checked here rather than trusted from step 1's already-verified
   *     result — cheap, and keeps this method's own contract self-contained
   * @param reply the assistant's reply text
   * @throws ResourceNotFoundException if the conversation vanished between steps 1 and 3, or isn't
   *     owned by {@code userId} — practically unreachable since step 1 just created or verified it,
   *     but a real error is safer here than an assumption
   */
  private void appendAssistantReply(UUID conversationId, UUID userId, String reply) {
    Conversation conversation =
        conversationRepository
            .findByIdAndUserId(conversationId, userId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Conversation not found: " + conversationId));
    Message assistantMessage =
        Message.builder().role("assistant").content(reply).timestamp(Instant.now()).build();
    conversation.addMessage(assistantMessage);
    conversationRepository.save(conversation);
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

  /**
   * The result of step 1 ({@link #loadHistoryAndAppendUserMessage}), carried across the transaction
   * boundary into the (transaction-free) model call and then into step 3.
   *
   * @param conversationId the conversation's id, freshly assigned if this turn started it
   * @param history the windowed conversation history, as detached, transaction-independent Spring
   *     AI message objects — safe to read after the persistence context that produced them closes
   */
  private record ConversationSnapshot(
      UUID conversationId, List<org.springframework.ai.chat.messages.Message> history) {}
}
