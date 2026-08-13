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
  },
  statusBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.75rem',
    padding: '0.4rem 0.9rem',
    borderRadius: '20px',
    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.04)',
    backdropFilter: 'blur(8px)',
    border: `1px solid ${theme.palette.divider}`,
    transition: 'all 0.2s ease-in-out',
    cursor: 'pointer',
    '&:hover': {
      backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.08)',
      borderColor: theme.palette.primary.main,
    },
  },
  pulseDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    display: 'inline-block',
    boxShadow: '0 0 6px currentColor',
  },
  onlineDot: {
    backgroundColor: '#4caf50',
    color: '#4caf50',
  },
  standbyDot: {
    backgroundColor: '#ff9800',
    color: '#ff9800',
  },
  offlineDot: {
    backgroundColor: '#f44336',
    color: '#f44336',
  },
  providerText: {
    fontWeight: 600,
    color: theme.palette.text.primary,
    fontSize: '0.82rem',
  },
  metaItem: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.35rem',
    fontSize: '0.78rem',
    color: theme.palette.text.secondary,
  },
  divider: {
    width: '1px',
    height: '14px',
    backgroundColor: theme.palette.divider,
  },
  copyrightText: {
    marginTop: '0.75rem',
    fontSize: '0.75rem',
    opacity: 0.7,
  },
  dialogContent: {
    minWidth: '280px',
    padding: '1.5rem',
  },
  targetSelect: {
    marginTop: '1rem',
    width: '100%',
  },
}));
