import { makeStyles } from 'tss-react/mui';

export default makeStyles()((theme) => ({
  searchContainer: {
    display: 'flex',
    justifyContent: 'center',
    flex: 1,
    margin: '0 20px',
    [theme.breakpoints.down('sm')]: {
      width: '100%',
      margin: 0,
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
    width: '100%',
    maxWidth: '1200px', // Allow the omnibox to be reasonably wide
    [theme.breakpoints.up('sm')]: {
      width: '800px',
      maxWidth: 'calc(100vw - 250px)', // Prevent overflow in navbar
    },
    [theme.breakpoints.up('md')]: {
      width: '1000px',
    },
  },
  input: {
    color: '#fff',
    [theme.breakpoints.down('sm')]: {
      marginTop: '-10px',
      marginBottom: '10px',
    },
    // Premium Omnibox styling
    padding: '12px 16px',
    borderRadius: '24px',
    backgroundColor: theme.palette.mode === 'light' ? 'rgba(255, 255, 255, 0.15)' : 'rgba(255, 255, 255, 0.05)',
    transition: 'background-color 0.2s ease, box-shadow 0.2s ease',
    '&:hover': {
      backgroundColor: theme.palette.mode === 'light' ? 'rgba(255, 255, 255, 0.25)' : 'rgba(255, 255, 255, 0.08)',
    },
    '&:focus-within': {
      backgroundColor: theme.palette.mode === 'light' ? 'rgba(255, 255, 255, 0.35)' : '#1e1e1e',
      boxShadow: theme.palette.mode === 'light'
        ? '0 4px 20px rgba(0,0,0,0.15)'
        : '0 4px 20px rgba(0,0,0,0.5)',
    },
  },
}));
