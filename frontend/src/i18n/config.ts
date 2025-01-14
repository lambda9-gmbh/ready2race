import i18next from 'i18next'
import {initReactI18next} from 'react-i18next'
import I18nextBrowserLanguageDetector from 'i18next-browser-languagedetector'
import enTrans from './en/translations.json'
import deTrans from './de/translations.json'

export const defaultNS = 'translations'
export const resources = {
    en: {
        translations: enTrans,
    },
    de: {
        translations: deTrans,
    },
} as const

i18next.use(I18nextBrowserLanguageDetector).use(initReactI18next).init({
    resources,
    defaultNS,
    fallbackLng: 'en',
})
