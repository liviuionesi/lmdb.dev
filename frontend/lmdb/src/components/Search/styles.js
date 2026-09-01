import { makeStyles } from 'tss-react/mui';

export default makeStyles()((theme) => ({
  searchContainer: {
    [theme.breakpoints.down('sm')]: {
      display: 'flex',
      justifyContent: 'center',
      width: '100%',
    },
  },
  // Wraps the TextField so the #209 highlight overlay (a sibling, not a child of the TextField
  // itself — MUI's Input/InputBase doesn't expose a slot for injecting arbitrary markup alongside
  // the native <input>) has a positioned ancestor to be absolutely placed against. inline-block
  // keeps this wrapper sized to the TextField's own intrinsic width, same as before this Task, when
  // the TextField was NavBar's direct flex child with nothing wrapping it.
  fieldWrapper: {
    position: 'relative',
    display: 'inline-block',
    verticalAlign: 'middle',
  },
  input: {
    color: theme.palette.mode === 'light' && 'black',
    filter: theme.palette.mode === 'light' && 'invert(1)',
    [theme.breakpoints.down('sm')]: {
      marginTop: '-10px',
      marginBottom: '10px',
    },
  },
}));
