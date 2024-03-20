import { makeStyles } from '@mui/styles';
// the () => ({}) means instantly return an object, instead of defining a callback function, which makestyles expects as argument
export default makeStyles((theme) => ({
  imageLink: {
    display: 'flex',
    justifyContent: 'content',
    padding: '10% 0',
  },
  image: {
    width: '70%',
  },
  links: {
    color: theme.palette.text.primary,
    textDecoration: 'none',
  },
  genreImage: {
    filter: theme.palette.mode === 'dark' ? 'dark' : 'invert(1)',
  },
}));
