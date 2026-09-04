import { makeStyles } from 'tss-react/mui';

// Positioned on the opposite (left) side of the screen from VoiceControl.jsx's mic Fab and
// language toggle (both fixed to the bottom-right), so this widget's own launcher + panel never
// stacks on top of them (#223 AC3's placement decision — see ChatWidget.jsx's Notes).
export default makeStyles()((theme) => ({
  launcher: {
    position: 'fixed',
    left: 20,
    bottom: 40,
    zIndex: 1201,
  },
  panel: {
    position: 'fixed',
    left: 20,
    bottom: 100,
    zIndex: 1201,
    width: 320,
    maxWidth: 'calc(100vw - 40px)',
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: theme.spacing(1, 1.5),
    borderBottom: `1px solid ${theme.palette.divider}`,
  },
  messageList: {
    flex: 1,
    minHeight: 200,
    maxHeight: 320,
    overflowY: 'auto',
    padding: theme.spacing(1.5),
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1),
  },
  message: {
    padding: theme.spacing(0.75, 1.25),
    borderRadius: theme.spacing(1.5),
    maxWidth: '80%',
    wordBreak: 'break-word',
  },
  messageUser: {
    alignSelf: 'flex-end',
    backgroundColor: theme.palette.primary.main,
    color: theme.palette.primary.contrastText,
  },
  messageAssistant: {
    alignSelf: 'flex-start',
    backgroundColor: theme.palette.action.selected,
    color: theme.palette.text.primary,
  },
  inputRow: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
    padding: theme.spacing(1, 1.5),
    borderTop: `1px solid ${theme.palette.divider}`,
  },
  errorAlert: {
    margin: theme.spacing(0, 1.5, 1),
  },
  typingIndicator: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(0.75),
  },
  // Standard clip-based visually-hidden pattern (content stays in the accessibility tree and is
  // announced by a screen reader via the aria-live region it's attached to, but never rendered on
  // screen) — used for the #225 AC4 "new assistant message" announcement, which would otherwise
  // duplicate the visible bubble for sighted users.
  visuallyHidden: {
    position: 'absolute',
    width: 1,
    height: 1,
    padding: 0,
    margin: -1,
    overflow: 'hidden',
    clip: 'rect(0, 0, 0, 0)',
    whiteSpace: 'nowrap',
    border: 0,
  },
}));
