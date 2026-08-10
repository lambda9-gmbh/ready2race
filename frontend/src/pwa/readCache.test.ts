import {describe, expect, it} from 'vitest'
import {
    CACHE_MAX_AGE_MS,
    clearCachedReads,
    readCachedRead,
    writeCachedRead,
} from './readCache.ts'

const fakeStore = () => {
    const data = new Map<string, string>()
    return {
        getItem: (k: string) => data.get(k) ?? null,
        setItem: (k: string, v: string) => void data.set(k, v),
        removeItem: (k: string) => void data.delete(k),
        key: (i: number) => [...data.keys()][i] ?? null,
        get length() {
            return data.size
        },
    }
}

describe('readCache', () => {
    it('gibt zurück, was hineingelegt wurde', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000, store)).toEqual({
            payload: {a: 1},
            fetchedAt: 5_000,
        })
    })

    it('gibt nichts für einen anderen Nutzer zurück', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('dashboard', 'u2', 'e1', 5_000, store)).toBeNull()
    })

    it('gibt nichts für ein anderes Event zurück', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('dashboard', 'u1', 'e2', 5_000, store)).toBeNull()
    })

    it('gibt nichts für eine andere Art zurück', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('events', 'u1', 'e1', 5_000, store)).toBeNull()
    })

    it('hält einen Eintrag knapp unter zwölf Stunden', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        const entry = readCachedRead('dashboard', 'u1', 'e1', 5_000 + CACHE_MAX_AGE_MS - 1, store)
        expect(entry?.payload).toEqual({a: 1})
    })

    it('verwirft einen Eintrag nach zwölf Stunden und räumt ihn weg', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000 + CACHE_MAX_AGE_MS + 1, store)).toBeNull()
        expect(store.length).toBe(0)
    })

    it('verwirft kaputten Inhalt, statt zu werfen', () => {
        const store = fakeStore()
        store.setItem('r2r.readCache.dashboard.u1.e1', '{kein json')
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000, store)).toBeNull()
    })

    it('gibt nichts aus einem leeren Speicher zurück', () => {
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000, fakeStore())).toBeNull()
    })

    it('löscht beim Abmelden nur die eigenen Einträge', () => {
        const store = fakeStore()
        writeCachedRead('dashboard', 'u1', 'e1', {a: 1}, 5_000, store)
        store.setItem('fremd', 'bleibt')
        clearCachedReads(store)
        expect(store.getItem('fremd')).toBe('bleibt')
        expect(readCachedRead('dashboard', 'u1', 'e1', 5_000, store)).toBeNull()
    })
})
