package dev.lmdb.ai.dto;

/**
 * The color theme a {@link VoiceCommandType#CHANGE_MODE} voice command selects (#214), mirroring
 * the two values {@code ColorModeContext}'s {@code setMode} accepts on the frontend.
 */
public enum ThemeMode {

  /** Switch to the dark theme. */
  DARK,

  /** Switch to the light theme. */
  LIGHT
}
