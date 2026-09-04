import React, { useEffect, useRef, useState } from 'react';
import {
  Fab, Paper, Typography, IconButton, TextField, Tooltip,
} from '@mui/material';
import {
  AddComment as NewConversationIcon,
  Chat as ChatIcon,
  Close as CloseIcon,
  Send as SendIcon,
} from '@mui/icons-material';

import { useSendChatMessageMutation } from '../../services/AI';
import {
  clearStoredConversationId,
  getStoredConversationId,
  setStoredConversationId,
} from '../../utils/chatConversation';
import useStyles from './styles';

/**
 * One bubble in the message list: right-aligned/primary-colored for the user, left-aligned/
 * neutral for the assistant (#223 AC2) — the only thing that tells the two apart, since neither
 * role renders a name or avatar.
 *
 * @param {{ role: 'user'|'assistant', text: string }} props
 * @returns {JSX.Element}
 */
export function ChatMessage({ role, text }) {
  const { classes, cx } = useStyles();
  const isUser = role === 'user';

  return (
    <Paper
      elevation={0}
      className={cx(classes.message, isUser ? classes.messageUser : classes.messageAssistant)}
      // The role is already conveyed visually (alignment + color); this repeats it for assistive
      // tech, which can't infer "sent by me" from screen position the way a sighted user can.
      aria-label={isUser ? 'You' : 'Assistant'}
    >
      <Typography variant="body2">{text}</Typography>
    </Paper>
  );
}

/**
 * Chat assistant widget (#223, wired to the backend by #224): a persistent launcher Fab
 * (mirroring VoiceControl.jsx's own fixed-position pattern) that opens a floating panel with a
 * message list and an input.
 *
 * <p>Sending a message posts it to {@code POST /api/v1/ai/chat} (via {@code
 * useSendChatMessageMutation}, `services/AI.js`) and appends the assistant's reply once it
 * arrives. The conversation id the backend returns is persisted via `utils/chatConversation.js`
 * (survives a page reload) and sent on every subsequent message so the conversation continues
 * server-side (#197 AC2); the very first message of a brand-new conversation omits it entirely
 * (#197 AC3). The header's "Start a new conversation" button is the other half of that same AC
 * (#224): it clears the persisted id (and the visible history) so the *next* message starts a
 * genuinely new conversation rather than continuing the old one — distinct from a page reload,
 * which must NOT clear it. `handleSend` also refuses to fire a second request while one is still
 * in flight ({@code isSending}), since the backend assigns a fresh conversation id per call with
 * no `conversationId` in the request — two concurrent first-messages would otherwise each start
 * their own conversation and the slower response's id would silently overwrite the faster one's.
 * The remaining loading/error *visual* states (a spinner, a dedicated error bubble) are #225 —
 * this Task is data-fetching and conversation-id state only, so a failed send here still leaves
 * the user's typed message in the list (nothing removes it), but doesn't yet render one.
 *
 * <p>Placement (#223 AC3): a fixed launcher on the bottom-LEFT, deliberately the opposite corner
 * from VoiceControl's mic Fab + language toggle (both bottom-right) so the two persistent widgets
 * never stack or overlap on narrow viewports.
 *
 * @returns {JSX.Element}
 */
function ChatWidget() {
  const { classes } = useStyles();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  // Lazily seeded from localStorage (not re-read on every render) so a page reload continues the
  // same conversation (#197 AC2) — cleared only by `utils/chatConversation.js`'s
  // clearStoredConversationId, which "starting a genuinely new conversation" (#224's own AC,
  // distinct from a reload) is for.
  const [conversationId, setConversationId] = useState(() => getStoredConversationId());
  const [sendChatMessage, { isLoading: isSending }] = useSendChatMessageMutation();
  const inputRef = useRef(null);
  const launcherRef = useRef(null);
  const listEndRef = useRef(null);
  // Local monotonic counter for message keys/ids — the backend never assigns one, so this is the
  // only id either bubble (user or assistant) ever has; nothing here needs to survive a remount.
  const nextIdRef = useRef(0);

  const handleToggle = () => setOpen((wasOpen) => !wasOpen);

  // Closing via the header button (unlike closing via the launcher Fab itself, which stays
  // mounted and keeps focus automatically) would otherwise drop keyboard focus to the document
  // body once the panel unmounts — sends it back to the still-mounted launcher instead, so a
  // keyboard/screen-reader user doesn't lose their place (#223 AC4's focus-management half).
  const handleClose = () => {
    setOpen(false);
    launcherRef.current?.focus();
  };

  // #224's own AC: "starting a genuinely new conversation (not just page reload) clears the
  // stored id" — the header button below is what triggers this, as opposed to a reload (which
  // must leave the stored id alone so useState's lazy initializer above picks it back up).
  // Clearing the visible message list alongside it is deliberate too: leaving old bubbles on
  // screen while silently starting a new conversation server-side would show the user a
  // conversation that no longer matches what they're about to continue.
  const handleNewConversation = () => {
    clearStoredConversationId();
    setConversationId(null);
    setMessages([]);
    inputRef.current?.focus();
  };

  // #223 AC4: focus lands in the input the moment the panel opens, so a keyboard/screen-reader
  // user can start typing immediately instead of having to tab past the header controls.
  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  // Keeps the newest message in view without the user having to scroll manually, the same
  // always-scroll-to-latest behavior chat UIs conventionally use. scrollIntoView is guarded
  // separately from the ref itself — jsdom (this app's test environment) renders the element but
  // doesn't implement it, unlike a real browser.
  useEffect(() => {
    listEndRef.current?.scrollIntoView?.({ block: 'end' });
  }, [messages]);

  const handleSend = async () => {
    const trimmed = draft.trim();
    // The empty-input guard matches Search.jsx's own; the in-flight guard prevents a second send
    // firing (Enter-mash, or a click landing while a previous send is still pending) before
    // `conversationId` state has caught up with the first response — two concurrent
    // no-conversationId requests would otherwise each start their own backend conversation, and
    // whichever reply lands second would silently overwrite the first one's id in state/storage.
    if (!trimmed || isSending) return;

    // 1. Append the user's bubble and clear the draft immediately — this must not wait on (or be
    // undone by) the backend call, so the typed message is never lost on a failed send (#197 AC4;
    // the error bubble itself is #225's polish layer, not this Task's).
    nextIdRef.current += 1;
    setMessages((prev) => [...prev, { id: nextIdRef.current, role: 'user', text: trimmed }]);
    setDraft('');

    // 2. Send it — omitting conversationId entirely (not as null) when this is the first message
    // of a brand-new conversation (#197 AC3).
    try {
      const response = await sendChatMessage({ conversationId, message: trimmed }).unwrap();
      // 3. Persist the (new or echoed) conversation id so the next message continues it, and
      // across a page reload (#197 AC2).
      setConversationId(response.conversationId);
      setStoredConversationId(response.conversationId);
      nextIdRef.current += 1;
      setMessages((prev) => [...prev, { id: nextIdRef.current, role: 'assistant', text: response.reply }]);
    } catch (error) {
      // A dedicated error state (without discarding the message already in the list above) is
      // #225 — this Task only guarantees the failure doesn't crash the widget or silently drop
      // the user's input.
      // eslint-disable-next-line no-console
      console.error('Chat assistant request failed', error);
    }
  };

  // #223 AC4: Enter submits, matching Search.jsx's handleKeyPress; Shift+Enter is left free for a
  // multi-line draft rather than being swallowed as a (premature) send. Also checks
  // nativeEvent.isComposing (Search.jsx's single-line field doesn't) — an IME committing a
  // composed character (CJK input, in particular) also fires a real 'Enter' keypress, and this
  // free-text chat box is far more likely to receive that than a movie-title search field.
  const handleKeyPress = (event) => {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent?.isComposing) return;
    event.preventDefault();
    handleSend();
  };

  return (
    <>
      <Tooltip title={open ? 'Close chat assistant' : 'Chat with the AI assistant'}>
        <span className={classes.launcher}>
          <Fab
            ref={launcherRef}
            color="primary"
            onClick={handleToggle}
            aria-label={open ? 'Close chat assistant' : 'Open chat assistant'}
          >
            {open ? <CloseIcon /> : <ChatIcon />}
          </Fab>
        </span>
      </Tooltip>

      {open && (
        <Paper elevation={6} className={classes.panel} role="dialog" aria-label="Chat assistant">
          <div className={classes.header}>
            <Typography variant="subtitle1">Chat Assistant</Typography>
            <Tooltip title="Start a new conversation">
              <span>
                <IconButton
                  size="small"
                  onClick={handleNewConversation}
                  disabled={messages.length === 0}
                  aria-label="Start a new conversation"
                >
                  <NewConversationIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <IconButton size="small" onClick={handleClose} aria-label="Close chat panel">
              <CloseIcon fontSize="small" />
            </IconButton>
          </div>

          <div className={classes.messageList} data-testid="chat-message-list">
            {messages.map((message) => (
              <ChatMessage key={message.id} role={message.role} text={message.text} />
            ))}
            <div ref={listEndRef} />
          </div>

          <div className={classes.inputRow}>
            <TextField
              inputRef={inputRef}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="Ask about a movie…"
              variant="standard"
              fullWidth
              multiline
              maxRows={4}
              // Targets the real <textarea> (a plain `aria-label` prop instead lands on the
              // FormControl wrapper div, not the element a screen reader/user-event actually
              // focuses) — same reasoning as Search.jsx's own slotProps.htmlInput usage.
              slotProps={{ htmlInput: { 'aria-label': 'Chat message' } }}
            />
            <IconButton
              onClick={handleSend}
              disabled={!draft.trim() || isSending}
              aria-label="Send message"
              color="primary"
            >
              <SendIcon />
            </IconButton>
          </div>
        </Paper>
      )}
    </>
  );
}

export default ChatWidget;
