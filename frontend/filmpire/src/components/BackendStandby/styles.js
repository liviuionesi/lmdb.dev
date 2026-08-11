import { makeStyles } from 'tss-react/mui';

export default makeStyles()((theme) => ({
  modalBackdrop: {
    backdropFilter: 'blur(12px)',
    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(10, 15, 30, 0.75)' : 'rgba(240, 244, 255, 0.75)',
  },
  glassCard: {
    borderRadius: 20,
    border: '1px solid',
    borderColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.12)' : 'rgba(0, 0, 0, 0.08)',
    background: theme.palette.mode === 'dark'
      ? 'linear-gradient(135deg, rgba(20, 26, 48, 0.85) 0%, rgba(15, 20, 38, 0.95) 100%)'
      : 'linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(245, 248, 255, 0.90) 100%)',
    boxShadow: theme.palette.mode === 'dark'
      ? '0 20px 50px rgba(0, 0, 0, 0.5), 0 0 30px rgba(59, 130, 246, 0.15)'
      : '0 20px 50px rgba(0, 0, 0, 0.1), 0 0 30px rgba(59, 130, 246, 0.1)',
    backdropFilter: 'blur(16px)',
    maxWidth: 520,
    width: '92%',
    padding: theme.spacing(3.5),
    textAlign: 'center',
  },
  pulseCircle: {
    position: 'relative',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: theme.spacing(2),
  },
  stepContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1.2),
    marginTop: theme.spacing(2.5),
    textAlign: 'left',
  },
  stepItem: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1.5),
    padding: theme.spacing(1, 1.5),
    borderRadius: 10,
    transition: 'all 0.3s ease',
  },
  stepActive: {
    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(59, 130, 246, 0.15)' : 'rgba(59, 130, 246, 0.08)',
    border: '1px solid rgba(59, 130, 246, 0.3)',
  },
  stepCompleted: {
    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(16, 185, 129, 0.12)' : 'rgba(16, 185, 129, 0.08)',
    border: '1px solid rgba(16, 185, 129, 0.3)',
  },
}));
