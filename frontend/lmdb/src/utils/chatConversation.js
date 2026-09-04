/**
 * Persisted chat-assistant conversation id (#224, part of #197's chat assistant Story). Mirrors
 * the get/set-plus-localStorage shape of {@code getDictationLanguage}/{@code setDictationLanguage}
 * in {@code dictationLanguage.js} rather than introducing a new pattern.
 *
 * The id is stored (not just kept in component state) so it survives a page reload — #224's own
 * AC distinguishes that from "starting a genuinely new conversation", which is what {@link
 * clearStoredConversationId} is for.
 */

const CHAT_CONVERSATION_ID_STORAGE_KEY = 'lmdb_chat_conversation_id';

/**
 * Reads the persisted conversation id.
 *
 * @returns {string|null} the stored conversation id, or `null` if none is stored yet (a
 *   brand-new conversation, per #197 AC3) or `localStorage` isn't available (server-side render /
 *   test environment without a `window`).
 */
export function getStoredConversationId() {
  if (typeof window === 'undefined') {
    return null;
  }
  return localStorage.getItem(CHAT_CONVERSATION_ID_STORAGE_KEY);
}

/**
 * Persists the conversation id returned by `POST /api/v1/ai/chat`, so it survives a page reload
 * and is sent on every subsequent message in the same conversation (#197 AC2).
 *
 * @param {string} conversationId - the id to persist; ignored (a no-op) if blank, so a caller
 *   can't corrupt storage with a stray value.
 */
export function setStoredConversationId(conversationId) {
  if (typeof window === 'undefined' || !conversationId) {
    return;
  }
  localStorage.setItem(CHAT_CONVERSATION_ID_STORAGE_KEY, conversationId);
}

/**
 * Clears the persisted conversation id — what "starting a genuinely new conversation" (as opposed
 * to just reloading the page) means for storage (#224's own AC). The next message sent after this
 * omits `conversationId` again, so the backend starts a fresh conversation (#197 AC3).
 */
export function clearStoredConversationId() {
  if (typeof window === 'undefined') {
    return;
  }
  localStorage.removeItem(CHAT_CONVERSATION_ID_STORAGE_KEY);
}
