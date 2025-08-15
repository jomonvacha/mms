module.exports = {
  root: true,
  env: {browser: true, es2021: true, node: true},
  parser: '@typescript-eslint/parser',
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react/recommended',
    'plugin:react-hooks/recommended',
  ],
  parserOptions: {
    ecmaFeatures: {jsx: true},
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  settings: {react: {version: 'detect'}},
  plugins: ['@typescript-eslint', 'react', 'react-hooks'],
  rules: {
    'react/prop-types': 'off',
    '@typescript-eslint/no-unused-vars': ['warn', {argsIgnorePattern: '^_', varsIgnorePattern: '^_'}],
    'no-console': ['warn', {allow: ['warn', 'error']}],
    '@typescript-eslint/semi': ['error', 'always'],
    '@typescript-eslint/consistent-type-imports': 'warn',
  },
  ignorePatterns: ['node_modules', 'dist'],
};

