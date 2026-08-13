import { makeStyles } from 'tss-react/mui';

export default makeStyles()((theme) => ({
  container: {
    padding: '2rem 1.5rem 4rem 1.5rem',
    maxWidth: '1100px',
    margin: '0 auto',
  },
  heroBox: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    textAlign: 'center',
    marginBottom: '2.5rem',
    padding: '2.5rem 1.5rem',
    borderRadius: '16px',
    background: theme.palette.mode === 'dark'
      ? 'linear-gradient(145deg, rgba(245, 197, 24, 0.08) 0%, rgba(229, 9, 20, 0.05) 100%)'
      : 'linear-gradient(145deg, rgba(245, 197, 24, 0.12) 0%, rgba(229, 9, 20, 0.06) 100%)',
    border: `1px solid ${theme.palette.mode === 'dark' ? 'rgba(245, 197, 24, 0.2)' : 'rgba(245, 197, 24, 0.3)'}`,
    boxShadow: theme.palette.mode === 'dark'
      ? '0 10px 30px rgba(0, 0, 0, 0.5)'
      : '0 10px 30px rgba(0, 0, 0, 0.05)',
  },
  heroTitle: {
    fontWeight: 900,
    marginTop: '1.2rem',
    letterSpacing: '0.5px',
  },
  heroSubtitle: {
    color: theme.palette.text.secondary,
    maxWidth: '750px',
    marginTop: '0.75rem',
    fontSize: '1.05rem',
    lineHeight: 1.6,
  },
  sectionCard: {
    padding: '2rem',
    borderRadius: '14px',
    marginBottom: '2rem',
    border: `1px solid ${theme.palette.divider}`,
    backgroundColor: theme.palette.background.paper,
    boxShadow: theme.palette.mode === 'dark'
      ? '0 6px 20px rgba(0, 0, 0, 0.3)'
      : '0 6px 20px rgba(0, 0, 0, 0.03)',
  },
  creatorCard: {
    background: theme.palette.mode === 'dark'
      ? 'linear-gradient(135deg, rgba(30, 41, 59, 0.7) 0%, rgba(15, 23, 42, 0.7) 100%)'
      : 'linear-gradient(135deg, rgba(248, 250, 252, 0.9) 0%, rgba(241, 245, 249, 0.9) 100%)',
    border: `1px solid ${theme.palette.mode === 'dark' ? 'rgba(59, 130, 246, 0.3)' : 'rgba(59, 130, 246, 0.2)'}`,
  },
  tmdbCard: {
    background: theme.palette.mode === 'dark'
      ? 'linear-gradient(135deg, rgba(1, 180, 228, 0.08) 0%, rgba(144, 206, 161, 0.05) 100%)'
      : 'linear-gradient(135deg, rgba(1, 180, 228, 0.07) 0%, rgba(144, 206, 161, 0.05) 100%)',
    border: '1px solid rgba(1, 180, 228, 0.3)',
  },
  disclaimerBox: {
    padding: '1rem 1.25rem',
    borderRadius: '8px',
    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(0, 0, 0, 0.4)' : 'rgba(255, 255, 255, 0.8)',
    borderLeft: '4px solid #01b4e4',
    marginTop: '1.25rem',
  },
  buttonGroup: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '1rem',
    marginTop: '1.5rem',
  },
  techGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
    gap: '1.25rem',
    marginTop: '1.5rem',
  },
  techItem: {
    padding: '1.25rem',
    borderRadius: '10px',
    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.03)' : 'rgba(0, 0, 0, 0.02)',
    border: `1px solid ${theme.palette.divider}`,
  },
}));
