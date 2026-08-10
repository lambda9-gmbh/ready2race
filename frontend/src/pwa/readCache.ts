/**
 * Lese-Cache der Helfer-App.
 *
 * Liegt bewusst in der App-Schicht statt im Service Worker: Ein Runtime-Cache auf die API würde
 * Teilnehmerdaten mit Klarnamen und Jahrgängen in der CacheStorage ablegen — auf Geräten, die
 * sich mehrere Leute teilen. Hier ist der Schlüssel an die Nutzerin gebunden, das Abmelden räumt
 * auf, und die Oberfläche bekommt den Abrufzeitpunkt für ihr Veraltet-Banner mitgeliefert.
 *
 * Die zwölf Stunden liegen über dem Sechs-Stunden-Fenster der Sitzung: Der Cache soll nie der
 * begrenzende Faktor sein. Überlebt er die Sitzung, ist er ohne Belang, weil dann ohnehin der
 * Anmeldebildschirm kommt.
 */

import {WebStorageLike} from '@contexts/user/sessionToken.ts'

/** `key`/`length` braucht nur das Aufräumen, deshalb getrennt vom schmalen Schreib-/Lesetyp. */
type EnumerableStorage = WebStorageLike & Pick<Storage, 'key' | 'length'>

const PREFIX = 'r2r.readCache.'

export const CACHE_MAX_AGE_MS = 12 * 60 * 60 * 1000

export type CachedRead<T> = {
    payload: T
    fetchedAt: number
}

type StoredRead<T> = CachedRead<T> & {
    userId: string
    eventId: string
}

const browserStore = (): EnumerableStorage => localStorage

const keyFor = (kind: string, userId: string, eventId: string) =>
    `${PREFIX}${kind}.${userId}.${eventId}`

export const writeCachedRead = <T>(
    kind: string,
    userId: string,
    eventId: string,
    payload: T,
    now: number = Date.now(),
    store: WebStorageLike = browserStore(),
): void => {
    const stored: StoredRead<T> = {payload, fetchedAt: now, userId, eventId}
    try {
        store.setItem(keyFor(kind, userId, eventId), JSON.stringify(stored))
    } catch {
        // Voller Speicher darf den Abruf nicht scheitern lassen - der Cache ist Beiwerk.
    }
}

export const readCachedRead = <T>(
    kind: string,
    userId: string,
    eventId: string,
    now: number = Date.now(),
    store: WebStorageLike = browserStore(),
): CachedRead<T> | null => {
    const key = keyFor(kind, userId, eventId)
    const raw = store.getItem(key)
    if (raw === null) {
        return null
    }
    let stored: StoredRead<T>
    try {
        stored = JSON.parse(raw) as StoredRead<T>
    } catch {
        store.removeItem(key)
        return null
    }
    if (
        typeof stored?.fetchedAt !== 'number' ||
        stored.userId !== userId ||
        stored.eventId !== eventId
    ) {
        store.removeItem(key)
        return null
    }
    if (now - stored.fetchedAt > CACHE_MAX_AGE_MS) {
        store.removeItem(key)
        return null
    }
    return {payload: stored.payload, fetchedAt: stored.fetchedAt}
}

export const clearCachedReads = (store: EnumerableStorage = browserStore()): void => {
    const keys: string[] = []
    for (let i = 0; i < store.length; i++) {
        const key = store.key(i)
        if (key !== null && key.startsWith(PREFIX)) {
            keys.push(key)
        }
    }
    keys.forEach(key => store.removeItem(key))
}
