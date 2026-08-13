import { makeStyles } from 'tss-react/mui';

export default makeStyles()((theme) => ({
  footerContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: '3rem',
    padding: '1.5rem 1rem',
    borderTop: `1px solid ${theme.palette.divider}`,
    color: theme.palette.text.secondary,
    fontSize: '0.85rem',
    userSelect: 'none',
  },
  statusBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.5rem',
    padding: '0.35rem 0.85rem',
    borderRadius: '20px',
    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.04)' : 'rgba(0, 0, 0, 0.03)',
    border: `1px solid ${theme.palette.divider}`,
  },
  pulseDot: {
    width: '7px',
    height: '7px',
    borderRadius: '50%',
    display: 'inline-block',
  },
  onlineDot: {
    backgroundColor: '#4caf50',
    boxShadow: '0 0 6px rgba(76, 175, 80, 0.6)',
  },
  standbyDot: {
    backgroundColor: '#ff9800',
    boxShadow: '0 0 6px rgba(255, 152, 0, 0.6)',
  },
  offlineDot: {
    backgroundColor: '#f44336',
  },
  providerText: {
    fontWeight: 600,
    color: theme.palette.text.primary,
    fontSize: '0.8rem',
  },
  copyrightText: {
    marginTop: '0.6rem',
    fontSize: '0.74rem',
    opacity: 0.65,
  },
}));
