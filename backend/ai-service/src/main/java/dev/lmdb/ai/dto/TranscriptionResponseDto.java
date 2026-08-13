package dev.lmdb.ai.dto;

/**
 * Result of a speech-to-text request (#68).
 *
 * @param text the recognized text, empty (not null) if nothing was understood
 */
public record TranscriptionResponseDto(String text) {}
