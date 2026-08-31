package dev.lmdb.ai.service;

import dev.lmdb.ai.dto.VoiceCommandDto;
import dev.lmdb.ai.dto.VoiceCommandType;
import dev.lmdb.ai.security.PromptSanitizer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Classifies a transcribed voice command (English or German, any phrasing) into one of the
 * frontend's canonical commands — logout, theme toggle, genre/category browse, or search (#68) —
 * via LLM-based intent parsing. Replaces {@code voiceCommands.js}'s old per-language regex table
 * (#214, part of Story #200's bilingual voice control): that table only ever matched fixed English
 * phrases, so neither German transcripts nor phrasing variance within English resolved reliably.
 * Same structured-extraction pattern as {@link QueryParsingService} (#202, ADR-020) — reused rather
 * than inventing a second "transcript to intent" approach for this service.
 */
@Service
@Slf4j
public class VoiceCommandParsingService {

  private static final String SYSTEM_PROMPT =
      """
        You classify one transcribed voice command for a movie browsing app — spoken in English or
        German, in any phrasing — into exactly one of four canonical commands. Recognize phrasing
        variance within a language (e.g. "light mode", "switch to light mode", and "make it light"
        are the same command) rather than matching fixed phrases, and understand German directly
        rather than translating it first.

        - LOGOUT: the user wants to sign/log out.
        - CHANGE_MODE: the user wants to switch the color theme. Set "mode" to exactly DARK or
          LIGHT, uppercase.
        - CHOOSE_GENRE: the user wants to browse a genre or one of the three fixed categories
          top_rated, popular, upcoming — exactly these lowercase, underscored literals when one of
          them is meant, never a translation or a variant spelling. Set "genreOrCategory" to one of
          those three literals, or to one of the known genres listed in the user message, copied
          back exactly as given there.
        - SEARCH: anything else the user wants to find in the catalog — a title, a person, or a
          free-text query. Set "query" to what they want to search for, in the language it was
          spoken.

        If nothing above confidently applies, set "command" to null and leave every other field
        null too — never guess at a command just to fill the field.

        Respond with exactly one JSON object matching the target schema.
        """;

  private final ChatClient chatClient;

  /**
   * @param chatClientBuilder builder for the Spring AI {@link ChatClient} used to classify the
   *     command
   */
  public VoiceCommandParsingService(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
  }

  /**
   * Classifies one transcribed voice command.
   *
   * @param rawTranscript the caller-supplied transcript, from {@code SpeechToTextService}
   * @param genreNames the caller's current genre names, for resolving a spoken genre by name
   * @return the classified command; every field {@code null} when nothing confidently matched, or
   *     when the model's response couldn't be read as the target schema at all (an unparseable
   *     reply degrades to a search over the raw transcript instead — see {@link
   *     #searchFallback(String)} — the same defensive posture {@link QueryParsingService} takes
   *     toward a flaky model call, not a "no match" the caller could confuse with a genuine one)
   */
  public VoiceCommandDto parse(String rawTranscript, List<String> genreNames) {
    String sanitizedTranscript = PromptSanitizer.sanitize(rawTranscript);
    List<String> sanitizedGenres = PromptSanitizer.sanitizeAll(genreNames);
    log.info(
        "Parsing voice command ({} chars, {} known genres)",
        sanitizedTranscript.length(),
        sanitizedGenres.size());

    String userPrompt =
        sanitizedGenres.isEmpty()
            ? sanitizedTranscript
            : sanitizedTranscript + "\n\nKnown genres: " + String.join(", ", sanitizedGenres);

    VoiceCommandDto parsed;
    try {
      parsed =
          chatClient
              .prompt()
              .system(SYSTEM_PROMPT)
              .user(userPrompt)
              .call()
              .entity(VoiceCommandDto.class);
    } catch (Exception e) {
      // The model's response didn't match the target schema (ambiguous/malformed output, or an
      // invalid enum literal). Falling back to a search over the raw transcript — rather than
      // either propagating a 500 or returning a "no match" the caller can't tell apart from a
      // deliberate one — keeps voice control usable exactly the way the old regex table's
      // catch-all "search whatever was said" branch did.
      log.warn(
          "Voice-command parsing model call failed, falling back to search: {}", e.getMessage());
      return searchFallback(sanitizedTranscript);
    }

    return normalize(parsed, sanitizedTranscript);
  }

  /**
   * Validates and trims the model's raw extraction into a caller-safe result: a {@code
   * CHANGE_MODE}/{@code CHOOSE_GENRE}/{@code SEARCH} command missing the field it needs to act on
   * (a blank {@code genreOrCategory}/{@code query}, or a null {@code mode}) degrades to "no match"
   * rather than being dispatched with a hole in it — except {@code SEARCH} with a blank query,
   * which falls back to searching the raw transcript instead, so "search" (unlike a mis-set genre
   * or mode) never comes back empty-handed.
   *
   * @param parsed the model's raw extraction; {@code null} treated the same as a null {@code
   *     command} — nothing confidently matched
   * @param sanitizedTranscript the already-sanitized transcript, used as {@code SEARCH}'s fallback
   *     query
   * @return the validated command, or an all-null "no match" result
   */
  private static VoiceCommandDto normalize(VoiceCommandDto parsed, String sanitizedTranscript) {
    if (parsed == null || parsed.command() == null) {
      return NO_MATCH;
    }
    return switch (parsed.command()) {
      case LOGOUT -> new VoiceCommandDto(VoiceCommandType.LOGOUT, null, null, null);
      case CHANGE_MODE ->
          parsed.mode() == null
              ? NO_MATCH
              : new VoiceCommandDto(VoiceCommandType.CHANGE_MODE, parsed.mode(), null, null);
      case CHOOSE_GENRE -> {
        String value = trimToNull(parsed.genreOrCategory());
        yield value == null
            ? NO_MATCH
            : new VoiceCommandDto(VoiceCommandType.CHOOSE_GENRE, null, value, null);
      }
      case SEARCH -> {
        String query = trimToNull(parsed.query());
        yield query == null
            ? searchFallback(sanitizedTranscript)
            : new VoiceCommandDto(VoiceCommandType.SEARCH, null, null, query);
      }
    };
  }

  /**
   * Builds the command returned when the model's call or response couldn't be used at all, or when
   * it classified {@code SEARCH} without an actual query — a search over the raw, sanitized
   * transcript, the same catch-all the old regex table always fell back to for anything it didn't
   * otherwise recognize.
   *
   * @param sanitizedTranscript the already-sanitized transcript
   * @return a {@code SEARCH} command carrying {@code sanitizedTranscript} as its query
   */
  private static VoiceCommandDto searchFallback(String sanitizedTranscript) {
    return new VoiceCommandDto(VoiceCommandType.SEARCH, null, null, sanitizedTranscript);
  }

  /**
   * @param value a possibly-null, possibly-blank string
   * @return {@code value} trimmed, or {@code null} if it was null or blank
   */
  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** The result returned when nothing in the transcript confidently matches a canonical command. */
  private static final VoiceCommandDto NO_MATCH = new VoiceCommandDto(null, null, null, null);
}
