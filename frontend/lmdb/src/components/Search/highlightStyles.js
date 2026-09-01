import { makeStyles } from 'tss-react/mui';

// Distinct underline style (+ weight for negation) per QuerySpanCategory (#207) so the three
// categories are never conveyed by color alone (#209 AC1, WCAG 1.4.1) — the colors below only
// reinforce the distinction. `overlay` mirrors this search field's existing light-mode `filter:
// invert(1)` hack (see ./styles.js) so the highlight colors invert together with the rest of the
// search bar in light mode instead of looking inconsistent with it.
export default makeStyles()((theme) => ({
  overlay: {
    position: 'absolute',
    display: 'flex',
    alignItems: 'center',
    overflow: 'hidden',
    whiteSpace: 'pre',
    pointerEvents: 'none',
    fontFamily: 'inherit',
    fontSize: 'inherit',
    fontWeight: 'inherit',
    letterSpacing: 'inherit',
    lineHeight: 'inherit',
    color: theme.palette.mode === 'light' ? 'black' : theme.palette.common.white,
    filter: theme.palette.mode === 'light' ? 'invert(1)' : undefined,
  },
  // No whiteSpace here — already 'pre' on the parent .overlay, which every .segment inherits.
  segment: {},
  connector: {
    textDecorationLine: 'underline',
    textDecorationStyle: 'dotted',
    textDecorationColor: '#4FC3F7',
    textDecorationThickness: '2px',
    textUnderlineOffset: '3px',
  },
  negation: {
    textDecorationLine: 'underline',
    textDecorationStyle: 'wavy',
    textDecorationColor: '#FF8A65',
    textDecorationThickness: '2px',
    textUnderlineOffset: '3px',
    fontWeight: 700,
  },
  entity: {
    textDecorationLine: 'underline',
    textDecorationStyle: 'solid',
    textDecorationColor: '#81C784',
    textDecorationThickness: '2px',
    textUnderlineOffset: '3px',
  },
}));
