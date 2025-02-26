import {EmailLanguage, Scope} from '@api/types.gen.ts'
import {fallbackLng, isLanguage, Language} from "@i18n/config.ts";
import i18next from "i18next";

export const getRootElement = () => document.getElementById('ready2race-root')!

export const scopeLevel: Record<Scope, number> = {
    GLOBAL: 2,
    OWN: 1,
}

export const formRegexNumber: RegExp = /^\d+([.,]\d+)?$/

export const formRegexInteger: RegExp = /^-?\d+$/

export const formRegexCurrency: RegExp = /^-?(([1-9]\d*)|0)([.,]\d{2})?$/

export const touchSupported = () => {
    try {
        return ('ontouchstart' in window || navigator.maxTouchPoints) === true
    } catch {
        return false
    }
}

export const languageMapping: Record<Language, EmailLanguage> = {
    de: 'DE',
    en: 'EN',
}

export const i18nLanguage = (): Language => isLanguage(i18next.language) ? i18next.language : fallbackLng