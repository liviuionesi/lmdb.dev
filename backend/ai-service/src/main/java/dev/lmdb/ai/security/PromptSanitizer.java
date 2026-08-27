package dev.lmdb.ai.security;

import java.util.List;

/**
 * Neutralises text that is interpolated into a model prompt.
 *
 * <p>Anything a caller controls — or that reaches us from another service — is data, not
 * instructions. When such text is pasted into a prompt verbatim it can carry line breaks and
 * role-looking prefixes, and the model has no way to tell those apart from the structure the
 * service itself emitted. Flattening each value to a single line and capping its length removes the
 * ability to forge that structure.
 *
 * <p>Where a conversation's turns are involved, this is a second line of defence only: {@link
 * dev.lmdb.ai.service.ChatAssistantService} passes history as typed {@code UserMessage}/{@code
 * AssistantMessage} objects rather than concatenated text, so there is no textual role prefix left
 * to forge.
 */
public final class PromptSanitizer {

  /** Longest single value kept; anything past this is dropped. */
  private static final int MAX_VALUE_LENGTH = 200;

  /** Most values kept from one list, bounding total prompt growth. */
  private static final int MAX_VALUES = 50;

  /** Static-only utility. */
  private PromptSanitizer() {}

  /**
   * Flattens one value to a single, length-capped line.
   *
   * @param value the untrusted text, possibly {@code null}
   * @return the value with control characters (line breaks included) collapsed to single spaces and
   *     the result truncated; empty string when {@code value} is {@code null}
   */
  public static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    // 1. Control characters are what let injected text start a new "role:" line — collapse them
    //    (and any run of whitespace they leave behind) into single spaces.
    String flattened = value.replaceAll("\\p{Cntrl}+", " ").replaceAll("\\s+", " ").trim();
    // 2. Cap the length so one entry can't dominate the prompt or push the real instructions out
    //    of the model's context window.
    return flattened.length() <= MAX_VALUE_LENGTH
        ? flattened
        : flattened.substring(0, MAX_VALUE_LENGTH);
  }

  /**
   * Applies {@link #sanitize(String)} across a list, dropping blanks and capping the element count.
   *
   * @param values the untrusted values, possibly {@code null}
   * @return sanitised, non-blank values, at most {@link #MAX_VALUES} of them; never {@code null}
   */
  public static List<String> sanitizeAll(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .map(PromptSanitizer::sanitize)
        .filter(v -> !v.isEmpty())
        .limit(MAX_VALUES)
        .toList();
  }
}
