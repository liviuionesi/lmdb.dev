import React, { useEffect, useRef, useState } from 'react';
import {
  Fab, Paper, Typography, IconButton, TextField, Tooltip,
} from '@mui/material';
import { Chat as ChatIcon, Close as CloseIcon, Send as SendIcon } from '@mui/icons-material';

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
 * Chat assistant widget (#223): a persistent launcher Fab (mirroring VoiceControl.jsx's own
 * fixed-position pattern) that opens a floating panel with a message list and an input.
 *
 * <p>UI shell only for this Task — sending a message appends it to the local list but nothing
 * calls the backend yet; wiring to {@code POST /api/v1/ai/chat} with conversation persistence is
 * #224, and the loading/error states a real backend call needs are #225. Both build on the
 * `messages`/`handleSend` shape here rather than replacing it.
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
  const inputRef = useRef(null);
  const launcherRef = useRef(null);
  const listEndRef = useRef(null);
  // Local monotonic counter for message keys/ids — a real id (and the assistant's actual reply)
  // arrives with #224's backend wiring; nothing here needs to survive a remount.
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

  const handleSend = () => {
    const trimmed = draft.trim();
    if (!trimmed) return; // matches Search.jsx's own empty-input guard on submit

    nextIdRef.current += 1;
    setMessages((prev) => [...prev, { id: nextIdRef.current, role: 'user', text: trimmed }]);
    setDraft('');
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
              disabled={!draft.trim()}
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
