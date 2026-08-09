import { makeStyles } from 'tss-react/mui';

export default makeStyles()((theme) => ({
  card: {
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
  },
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: '50%',
    flexShrink: 0,
  },
  statusUp: {
    backgroundColor: theme.palette.success.main,
  },
  statusDown: {
    backgroundColor: theme.palette.error.main,
  },
  statusChecking: {
    backgroundColor: theme.palette.grey[500],
  },
  cardActions: {
    marginTop: 'auto',
  },
}));
