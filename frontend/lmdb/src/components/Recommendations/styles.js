import { makeStyles } from 'tss-react/mui';

export default makeStyles()((theme) => ({
  container: {
    display: 'flex',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    overflow: 'auto',
    [theme.breakpoints.down('sm')]: {
      justifyContent: 'center',
    },
  },
  message: {
    display: 'flex',
    alignItems: 'center',
    marginTop: '20px',
  },
}));
