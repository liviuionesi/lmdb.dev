package dev.lmdb.ai.dto;

/**
 * The canonical voice commands {@link dev.lmdb.ai.service.VoiceCommandParsingService} classifies a
 * transcribed voice command into (#214, Story #200). Matches the set the frontend's {@code
 * VoiceControl.jsx}/{@code runCommand} already dispatches on (#68) — the frontend-only "no
 * confident match" case (previously a {@code null} return from the old regex-based {@code
 * parseVoiceCommand}) is represented by a {@link VoiceCommandDto} whose {@code command} is itself
 * {@code null}, rather than a fifth enum constant here.
 */
public enum VoiceCommandType {

  /** The user wants to sign/log out. */
  LOGOUT,

  /** The user wants to switch the color theme; {@link VoiceCommandDto#mode()} carries which one. */
  CHANGE_MODE,

  /** The user wants to browse a genre, or one of the three fixed categories. */
  CHOOSE_GENRE,

  /** The user wants to search the catalog for a title, person, or other free-text query. */
  SEARCH
}
