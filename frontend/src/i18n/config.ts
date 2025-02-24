import i18next from 'i18next'
import {initReactI18next} from 'react-i18next'
import I18nextBrowserLanguageDetector from 'i18next-browser-languagedetector'
import enTrans from './en/translations.json'
import deTrans from './de/translations.json'
import {Locale as DateLocale} from 'date-fns'
import {de} from 'date-fns/locale/de'
import {Localization as MaterialLocale} from '@mui/material/locale'
import {Localization as DataGridLocale} from '@mui/x-data-grid/internals'
import {deDE as deMaterial, enUS as enMaterial} from '@mui/material/locale'
import {enUS as enDataGrid} from '@mui/x-data-grid/locales/enUS'
import {deDE as deDataGrid} from '@mui/x-data-grid/locales/deDE'
import {enUS as enDatePicker} from '@mui/x-date-pickers/locales/enUS'
import {deDE as deDatePicker} from '@mui/x-date-pickers/locales/deDE'
import {PickersLocaleText} from '@mui/x-date-pickers'

export const LANGUAGES = ['de', 'en'] as const
export type Language = typeof LANGUAGES[number]
export const isLanguage = (s: string): s is Language => LANGUAGES.includes(s as Language)

export type Locale = {
    date: DateLocale | undefined
    material: MaterialLocale
    dataGrid: DataGridLocale
    datePicker: Partial<PickersLocaleText<Date>>
}

export const locales: Record<Language, Locale> = {
    de: {
        date: de,
        material: deMaterial,
        dataGrid: deDataGrid,
        datePicker: deDatePicker.components.MuiLocalizationProvider.defaultProps.localeText,
    },
    en: {
        date: undefined,
        material: enMaterial,
        dataGrid: enDataGrid,
        datePicker: enDatePicker.components.MuiLocalizationProvider.defaultProps.localeText,
    },
}

export const fallbackLng: Language = 'en'
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
    fallbackLng,
})