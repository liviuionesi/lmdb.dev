// tss-react/mui's makeStyles is a drop-in for @mui/styles' hook API (removed
// in MUI v6, and its last release never supported React 19) — same
// `theme => ({ ... })` shape, curried through an extra `()` for tss-react's
// optional name/params argument. Consumers destructure `{ classes }` instead
// of getting classes back directly.
import { makeStyles } from 'tss-react/mui';

export default makeStyles()(() => ({
  image: {
    maxWidth: '90%',
    borderRadius: '20px',
    objectFit: 'cover',
    boxShadow: '0.5em 0.5em 1em',
  },
}));
