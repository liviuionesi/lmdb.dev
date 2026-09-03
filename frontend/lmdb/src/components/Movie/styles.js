import { makeStyles } from 'tss-react/mui';

export default makeStyles()((theme) => ({
  movie: {
    padding: '10px',
  },
  links: {
    alignItems: 'center',
    fontWeight: 'bolder',
    textDecoration: 'none',
    [theme.breakpoints.up('xs')]: {
      display: 'flex',
      flexDirection: 'column',
    },
    '&:hover': {
      cursor: 'pointer',
    },
  },
  image: {
    borderRadius: '20px',
    height: '300px',
    marginBottom: '10px',
    '&:hover': {
      transform: 'scale(1.05)',
    },
  },
  title: {
    color: theme.palette.text.primary,
    textOverflow: 'ellipsis',
    width: '230px',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    marginTop: '10px',
    marginBottom: 0,
    textAlign: 'center',
  },
  caption: {
    color: theme.palette.text.secondary,
    width: '230px',
    textAlign: 'center',
    marginTop: '6px',
  },
}));
