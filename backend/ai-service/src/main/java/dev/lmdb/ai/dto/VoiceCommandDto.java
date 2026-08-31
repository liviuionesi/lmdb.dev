package dev.lmdb.ai.dto;

/**
 * A voice command classified from a transcribed utterance (#214, Story #200) — response body for
 * {@code POST /api/v1/ai/voice-command}. Carries the same four fields the frontend's old regex-
 * based {@code parseVoiceCommand} (#68) returned, now populated by {@link
 * dev.lmdb.ai.service.VoiceCommandParsingService}'s LLM-based intent parsing instead of a
 * per-language regex table.
 *
 * @param command the classified command, or {@code null} when nothing confidently matched — every
 *     other field is {@code null} in that case too, same as the old {@code parseVoiceCommand}
 *     returning {@code null} outright
 * @param mode the theme to switch to, set only when {@code command} is {@link
 *     VoiceCommandType#CHANGE_MODE}
 * @param genreOrCategory the genre name or fixed category ({@code top_rated}/{@code popular}/{@code
 *     upcoming}) to browse, set only when {@code command} is {@link VoiceCommandType#CHOOSE_GENRE}
 * @param query the free-text search query, set only when {@code command} is {@link
 *     VoiceCommandType#SEARCH}
 */
public record VoiceCommandDto(
    VoiceCommandType command, ThemeMode mode, String genreOrCategory, String query) {}
