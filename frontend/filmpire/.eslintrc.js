module.exports = {
  parser:  "@babel/eslint-parser",
  extends: ["plugin:react/recommended", "airbnb"],
  env: {
    browser: true,
    es6: true,
  },
  // ESLint has no built-in "vitest" env (unlike "jest"), and adding
  // eslint-plugin-vitest is out of scope for a test-runner swap (#127), so
  // the handful of Vitest globals the test suite actually uses (from
  // `test.globals: true` in vite.config.js) are declared by hand instead.
  globals: {
    describe: "readonly",
    it: "readonly",
    expect: "readonly",
    beforeEach: "readonly",
    afterEach: "readonly",
    vi: "readonly",
  },
  parserOptions: {
    ecmaVersion: 2021,
    sourceType: "module",
    ecmaFeatures: {
      jsx: true,
    },
    // requireConfigFile: false + an inline preset lets @babel/eslint-parser
    // parse JSX without a project-wide Babel config file. Previously relied
    // on .babelrc's "babel-preset-react-app", which shipped inside
    // react-scripts and no longer exists now that Vite (esbuild) has
    // replaced it as the actual build/transform toolchain (#125).
    requireConfigFile: false,
    babelOptions: {
      presets: ['@babel/preset-react'],
    },
  },
  settings: {
    react: {
      version: "detect",
    },
  },
  plugins: ["react"],
  rules: {
    "import/no-cycle": 0,
    "no-console": 0,
    "react/prop-types": 0,
    "linebreak-style": 0,
    "react/state-in-constructor": 0,
    "import/prefer-default-export": 0,
    "max-len": [2, 250],
    "object-curly-newline": 0,
    "react/jsx-filename-extension": 0,
    "react/jsx-one-expression-per-line": 0,
    "jsx-a11y/click-events-have-key-events": 0,
    "jsx-a11y/alt-text": 0,
    "jsx-a11y/no-autofocus": 0,
    "jsx-a11y/no-static-element-interactions": 0,
    "react/no-array-index-key": 0,
    "no-param-reassign": 0,
    "react/react-in-jsx-scope": 0,
    "react/jsx-props-no-spreading": 0,
    "no-sparse-arrays": 0,
    "no-array-index-key": 0,
    camelcase: 0,
  },
};
