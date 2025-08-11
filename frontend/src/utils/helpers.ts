import {EmailLanguage, Scope} from '@api/types.gen.ts'
import {fallbackLng, isLanguage, Language} from '@i18n/config.ts'
import i18next from 'i18next'

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

export const i18nLanguage = (): Language =>
    isLanguage(i18next.language) ? i18next.language : fallbackLng

export const groupBy = <T, K>(list: T[], keyGetter: (v: T) => K): Map<K, T[]> => {
    const map = new Map()
    list.forEach(item => {
        const key = keyGetter(item)
        const collection = map.get(key)
        if (!collection) {
            map.set(key, [item])
        } else {
            collection.push(item)
        }
    })
    return map
}

export const adminId = '00000000-0000-0000-0000-000000000000'

export const eventRegistrationPossible = (from?: string, to?: string) => {
    return (
        from !== undefined &&
        new Date(from) < new Date() &&
        (to === undefined || new Date(to) > new Date())
    )
}

export const isFromUnion = <A extends string>(s: string | undefined, u: readonly A[]): s is A =>
    u.includes(s as A)

export const arrayOfNotNull = <T>(...args: (T | null)[]): T[] => {
    return args.filter(a => a !== null)
}

export const ifDefined = <T, R>(value: T | null | undefined, f: (value: T) => R): R | null =>
    value !== null && value !== undefined ? f(value) : null

export const shuffle = <T>(list: T[]) => {
    const newList: T[] = {...list}
    let currentIndex = list.length

    while (currentIndex != 0) {
        let randomIndex = Math.floor(Math.random() * currentIndex)
        currentIndex--
        const currentValue = newList[currentIndex]
        newList[currentIndex] = newList[randomIndex]
        newList[randomIndex] = currentValue
    }
    return newList
}

export const a11yProps = <TabType>(name: string, index: TabType) => {
    return {
        value: index,
        id: `${name}-tab-${index}`,
        'aria-controls': `${name}-tabpanel-${index}`,
    }
}

export const getFilename = (response: Response): string | undefined => {
    const disposition = response.headers.get('Content-Disposition')

    return (
        disposition?.match(/attachment; filename="(.+)"/)?.[1] ??
        disposition?.match(/attachment; filename=(.+)/)?.[1]
    )
}
