/**
 * Ablage des Sitzungstokens.
 *
 * Beide Oberflächen legen ihren Token in `localStorage` ab, unter getrennten Schlüsseln:
 * Helfer-Sitzungen unter `/app` als `session.app`, damit die installierte App einen Neustart
 * durch das Betriebssystem übersteht — Schiedsrichter legen das Telefon weg und schauen eine
 * Stunde später wieder drauf. Die Verwaltungsoberfläche als `session.admin`, damit die Anmeldung
 * einen neuen Tab überlebt; vorher lag sie in `sessionStorage` und war per Definition an den
 * einen Tab gebunden. Der bewusste Trade-off: Auf einem geteilten Rechner schützt jetzt nicht
 * mehr das Schließen des Tabs, sondern nur noch ein explizites Abmelden bzw. der Ablauf der
 * Sechs-Stunden-Frist.
 *
 * Die Frist von sechs Stunden spiegelt das gleitende Fenster des Servers (AuthService.kt:
 * `tokenLifetime = 6.hours`, bei jedem Request neu gesetzt). Ein älterer Token wäre serverseitig
 * ohnehin ungültig; ihn hier zu verwerfen spart den 401-Umweg.
 *
 * Übergang: Findet die Verwaltung keinen `localStorage`-Eintrag, aber noch den alten
 * `sessionStorage`-Schlüssel `session`, wird der Token dorthin übernommen und der alte Schlüssel
 * entfernt — so loggt das Deploy niemanden aus. Der entsprechende Rückfall für Helfer-Sitzungen
 * (Lesen ohne Übernahme) bleibt aus demselben Grund bestehen.
 *
 * Der Speicher wird injiziert, weil die vitest-Umgebung `node` ist und dort weder `localStorage`
 * noch `sessionStorage` existiert.
 */

import {LoginDto} from '@api/types.gen.ts'

export type WebStorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export type TokenStores = {
    local: WebStorageLike
    session: WebStorageLike
}

const APP_KEY = 'session.app'
const ADMIN_KEY = 'session.admin'
const LEGACY_SESSION_KEY = 'session'
const USER_KEY = 'session.user'

export const SESSION_MAX_AGE_MS = 6 * 60 * 60 * 1000

type StoredToken = {
    token: string
    lastUsedAt: number
}

const browserStores = (): TokenStores => ({local: localStorage, session: sessionStorage})

const tokenKey = (isInApp: boolean): string => (isInApp ? APP_KEY : ADMIN_KEY)

const readStored = (stores: TokenStores, key: string): StoredToken | null => {
    const raw = stores.local.getItem(key)
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
    stores.local.removeItem(key)
    return null
}

const writeStored = (stores: TokenStores, key: string, token: string, now: number): void => {
    stores.local.setItem(key, JSON.stringify({token, lastUsedAt: now} satisfies StoredToken))
}

export const readSessionToken = (
    isInApp: boolean,
    now: number = Date.now(),
    stores: TokenStores = browserStores(),
): string | null => {
    const stored = readStored(stores, tokenKey(isInApp))
    if (stored === null) {
        const legacy = stores.session.getItem(LEGACY_SESSION_KEY)
        if (legacy === null) {
            return null
        }
        if (isInApp) {
            // Bestehende Helfer-Sitzung aus der Zeit vor der localStorage-Ablage: nur lesen.
            return legacy
        }
        // Verwaltung: alten Tab-gebundenen Token übernehmen, damit das Deploy niemanden
        // ausloggt. Die Frist startet jetzt - dass der Token noch im lebenden Tab liegt,
        // heißt, dass die Sitzung in Gebrauch ist.
        writeStored(stores, ADMIN_KEY, legacy, now)
        stores.session.removeItem(LEGACY_SESSION_KEY)
        return legacy
    }
    if (now - stored.lastUsedAt > SESSION_MAX_AGE_MS) {
        stores.local.removeItem(tokenKey(isInApp))
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
    writeStored(stores, tokenKey(isInApp), token, now)
    if (!isInApp) {
        // Kein veralteter Zweitstand: Der alte Tab-gebundene Schlüssel darf den frischen
        // Token nicht überleben.
        stores.session.removeItem(LEGACY_SESSION_KEY)
    }
}

/** Schiebt die Frist beider Ablagen nach vorn. Ohne gespeicherten Token ein No-op. */
export const touchSessionToken = (
    now: number = Date.now(),
    stores: TokenStores = browserStores(),
): void => {
    for (const key of [APP_KEY, ADMIN_KEY]) {
        const stored = readStored(stores, key)
        if (stored !== null) {
            writeStored(stores, key, stored.token, now)
        }
    }
}

export const clearSessionToken = (
    isInApp: boolean,
    stores: TokenStores = browserStores(),
): void => {
    if (isInApp) {
        stores.local.removeItem(APP_KEY)
        stores.local.removeItem(USER_KEY)
    } else {
        stores.local.removeItem(ADMIN_KEY)
    }
    // Der alte Tab-gebundene Schlüssel ist Rückfall- bzw. Übernahmequelle beider Oberflächen -
    // bliebe er stehen, wäre die Abmeldung keine.
    stores.session.removeItem(LEGACY_SESSION_KEY)
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
        stores.local.setItem(USER_KEY, JSON.stringify(user))
    }
}

export const readSessionUser = (
    isInApp: boolean,
    stores: TokenStores = browserStores(),
): LoginDto | null => {
    if (!isInApp) {
        return null
    }
    const raw = stores.local.getItem(USER_KEY)
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
    stores.local.removeItem(USER_KEY)
    return null
}
