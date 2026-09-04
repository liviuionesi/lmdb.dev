// Tests chatConversation.js's get/set/clear localStorage wrapper (#224): the
// no-conversation-yet default, that the stored id round-trips, that setStoredConversationId
// ignores a blank value rather than corrupting storage, and that clearStoredConversationId is
// what "starting a genuinely new conversation" (as opposed to a page reload) means for storage.
import {
  getStoredConversationId,
  setStoredConversationId,
  clearStoredConversationId,
} from './chatConversation';

const STORAGE_KEY = 'lmdb_chat_conversation_id';

describe('chatConversation', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('returns null when no conversation has been started yet', () => {
    // Given a first-time visitor (no localStorage entry), when the conversation id is read, then
    // it must be null, not an empty string or undefined the caller would need to special-case
    // before deciding whether to send it (#197 AC3).
    expect(getStoredConversationId()).toBeNull();
  });

  it('persists a conversation id across the get/set round trip', () => {
    // Given the backend returns a new conversation id, when it's stored and then read back
    // (simulating the next page load), then it's the value that was set — proves
    // setStoredConversationId actually reaches localStorage rather than just updating some
    // in-memory value get ignores (#197 AC2's "survives a page reload").
    setStoredConversationId('11111111-1111-1111-1111-111111111111');
    expect(getStoredConversationId()).toBe('11111111-1111-1111-1111-111111111111');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('11111111-1111-1111-1111-111111111111');
  });

  it('ignores a blank id instead of writing it to storage', () => {
    // Given a valid id is already persisted, when setStoredConversationId is called with
    // null/undefined/empty (e.g. a response shape bug), then storage is left untouched rather
    // than corrupted with a value that would break every subsequent message.
    setStoredConversationId('11111111-1111-1111-1111-111111111111');
    setStoredConversationId(null);
    setStoredConversationId('');
    expect(getStoredConversationId()).toBe('11111111-1111-1111-1111-111111111111');
  });

  it('clears the stored id, so the next message omits conversationId again', () => {
    // Given an ongoing conversation, when clearStoredConversationId is called (what "starting a
    // genuinely new conversation" means, distinct from a page reload — #224's own AC), then a
    // subsequent read comes back null, the same as a first-time visitor.
    setStoredConversationId('11111111-1111-1111-1111-111111111111');

    clearStoredConversationId();

    expect(getStoredConversationId()).toBeNull();
  });
});
