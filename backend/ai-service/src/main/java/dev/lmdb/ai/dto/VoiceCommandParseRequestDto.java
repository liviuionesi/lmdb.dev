package dev.lmdb.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/ai/voice-command} (#214, Story #200) — the transcript {@code
 * SpeechToTextService} (#68/#212) produced, plus the caller's current genre catalog so {@link
 * dev.lmdb.ai.service.VoiceCommandParsingService} can resolve a spoken genre to one {@code
 * genreNames} actually lists, the same list the frontend's old regex-based {@code
 * parseVoiceCommand} matched against client-side.
 *
 * @param transcript the transcribed voice command text
 * @param genreNames the caller's current genre names, for matching a spoken genre by name; empty
 *     (never {@code null}) when the caller has none loaded yet
 */
public record VoiceCommandParseRequestDto(@NotBlank String transcript, List<String> genreNames) {

  /**
   * @param transcript see the field Javadoc above
   * @param genreNames see the field Javadoc above; defaulted to {@link List#of()} when the caller
   *     omits it — Jackson leaves an omitted record component {@code null} rather than failing
   *     deserialization, so without this a missing key would reach {@link
   *     dev.lmdb.ai.service.VoiceCommandParsingService#parse} as {@code null}
   */
  public VoiceCommandParseRequestDto {
    genreNames = genreNames == null ? List.of() : genreNames;
  }
}
