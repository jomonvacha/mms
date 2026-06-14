import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import en from './locales/en.json'

/**
 * i18n bootstrap. Kept minimal — single English bundle loaded statically at
 * startup, no detector, no runtime loader. To add a language:
 *
 *   1. Copy `src/locales/en.json` to `src/locales/<lang>.json` and translate.
 *   2. Import it below and register under `resources.<lang>.translation`.
 *   3. Add a switcher (preferences screen) that calls `i18n.changeLanguage(lang)`.
 *
 * We don't plug in the HTTP backend yet because the app has one user of i18n
 * (AdminModels) and the whole bundle is ~5KB. If that changes, add
 * `i18next-http-backend` and load bundles on demand.
 */
void i18n
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
    },
    lng: 'en',
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false, // React already escapes
    },
    // Missing keys are harmless but noisy — promote to warn during dev.
    saveMissing: false,
    returnEmptyString: false,
  })

export default i18n
