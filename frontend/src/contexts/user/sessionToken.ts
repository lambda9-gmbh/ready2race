/**
 * Ablage des Sitzungstokens.
 *
 * Für die Verwaltungsoberfläche bleibt alles beim Alten: `sessionStorage`, weg beim Schließen
 * des Tabs. Helfer-Sitzungen unter `/app` liegen dagegen in `localStorage`, damit die
 * installierte App einen Neustart durch das Betriebssystem übersteht — Schiedsrichter legen das
 * Telefon weg und schauen eine Stunde später wieder drauf.
 *
 * Die Frist von sechs Stunden spiegelt das gleitende Fenster des Servers (AuthService.kt:
 * `tokenLifetime = 6.hours`, bei jedem Request neu gesetzt). Ein älterer Token wäre serverseitig
 * ohnehin ungültig; ihn hier zu verwerfen spart den 401-Umweg.
 *
 * Der Speicher wird injiziert, weil die vitest-Umgebung `node` ist und dort weder `localStorage`
 * noch `sessionStorage` existiert.
 */

import {LoginDto} from '@api/types.gen.ts'

export type WebStorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export type TokenStores = {
    app: WebStorageLike
    session: WebStorageLike
}

const APP_KEY = 'session.app'
const SESSION_KEY = 'session'
const USER_KEY = 'session.user'

export const SESSION_MAX_AGE_MS = 6 * 60 * 60 * 1000

type StoredToken = {
    token: string
    lastUsedAt: number
}

const browserStores = (): TokenStores => ({app: localStorage, session: sessionStorage})

const readStored = (stores: TokenStores): StoredToken | null => {
    const raw = stores.app.getItem(APP_KEY)
    if (raw === null) {
        return null
    }
    try {
        const parsed = JSON.parse(raw) as StoredToken
        if (typeof parsed?.token === 'string' && typeof parsed?.lastUsedAt === 'number') {
            return parsed
        }
    } catch {
        // fällt unten auf Aufräumen durch
    }
    stores.app.removeItem(APP_KEY)
    return null
}

export const readSessionToken = (
    isInApp: boolean,
    now: number = Date.now(),
    stores: TokenStores = browserStores(),
): string | null => {
    if (!isInApp) {
        return stores.session.getItem(SESSION_KEY)
    }
    const stored = readStored(stores)
    if (stored === null) {
        return stores.session.getItem(SESSION_KEY)
    }
    if (now - stored.lastUsedAt > SESSION_MAX_AGE_MS) {
        stores.app.removeItem(APP_KEY)
        return null
    }
    return stored.token
}

export const writeSessionToken = (
    token: string,
    isInApp: boolean,
    now: number = Date.now(),
    stores: TokenStores = browserStores(),
): void => {
    if (isInApp) {
        stores.app.setItem(APP_KEY, JSON.stringify({token, lastUsedAt: now} satisfies StoredToken))
    } else {
        stores.session.setItem(SESSION_KEY, token)
    }
}

/** Schiebt die Frist nach vorn. Ohne gespeicherten Helfer-Token ein No-op. */
export const touchSessionToken = (
    now: number = Date.now(),
    stores: TokenStores = browserStores(),
): void => {
    const stored = readStored(stores)
    if (stored !== null) {
        stores.app.setItem(
            APP_KEY,
            JSON.stringify({token: stored.token, lastUsedAt: now} satisfies StoredToken),
        )
    }
}

export const clearSessionToken = (
    isInApp: boolean,
    stores: TokenStores = browserStores(),
): void => {
    if (isInApp) {
        stores.app.removeItem(APP_KEY)
        stores.app.removeItem(USER_KEY)
    }
    stores.session.removeItem(SESSION_KEY)
}

/**
 * Die Angaben zur angemeldeten Person - Kennung, Privilegien, Verein. Ohne sie wüsste die App
 * nach einem Kaltstart ohne Netz zwar, dass jemand angemeldet ist, aber nicht, was er darf.
 * Keine Namen, keine Mailadressen: LoginDto trägt nur Kennungen.
 */
export const writeSessionUser = (
    user: LoginDto,
    isInApp: boolean,
    stores: TokenStores = browserStores(),
): void => {
    if (isInApp) {
        stores.app.setItem(USER_KEY, JSON.stringify(user))
    }
}

export const readSessionUser = (
    isInApp: boolean,
    stores: TokenStores = browserStores(),
): LoginDto | null => {
    if (!isInApp) {
        return null
    }
    const raw = stores.app.getItem(USER_KEY)
    if (raw === null) {
        return null
    }
    try {
        const parsed = JSON.parse(raw) as LoginDto
        if (typeof parsed?.id === 'string' && Array.isArray(parsed?.privileges)) {
            return parsed
        }
    } catch {
        // fällt unten auf Aufräumen durch
    }
    stores.app.removeItem(USER_KEY)
    return null
}
