# Locale bundles

Static JSON bundles consumed by `react-i18next` via `src/i18n.ts`. One file
per language, all keys namespaced per screen (e.g. `adminModels.*`) so a
future refactor can split bundles without renaming keys.

## Add a new language

1. Copy `en.json` to `<lang>.json` (e.g. `es.json`, `fr.json`).
2. Translate the values — **keep the keys unchanged**.
3. Register it in `src/i18n.ts`:
   ```ts
   import es from './locales/es.json'
   // ...
   resources: {
     en: { translation: en },
     es: { translation: es },
   },
   ```
4. Surface a language switcher from user preferences. `i18n.changeLanguage('es')`
   persists via the existing `preferences.language` field on the user profile.

## Current coverage

| Screen | en |
|---|---|
| AdminModels | ✅ |
| Others | inline strings, migrate as needed |

Pragmatic rollout: new screens use `t()` from day one; existing screens
migrate opportunistically when they're edited for other reasons. Running
coverage counts aren't tracked — the namespace convention makes it cheap
to grep `/"inline": "/` when we want to sweep.
