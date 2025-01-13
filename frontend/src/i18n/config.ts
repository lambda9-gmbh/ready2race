import i18next from 'i18next'
import {initReactI18next} from 'react-i18next'
import I18nextBrowserLanguageDetector from 'i18next-browser-languagedetector'
import en from './en.json'
import de from './de.json'

export const resources = {
    en,
    de,
} as const

i18next.use(I18nextBrowserLanguageDetector).use(initReactI18next).init({
    resources,
    fallbackLng: 'en',
})
