import {describe, expect, it} from 'vitest'
import {
    clearSessionToken,
    readSessionToken,
    readSessionUser,
    SESSION_MAX_AGE_MS,
    touchSessionToken,
    TokenStores,
    writeSessionToken,
    writeSessionUser,
} from './sessionToken.ts'

const fakeStore = () => {
    const data = new Map<string, string>()
    return {
        getItem: (k: string) => data.get(k) ?? null,
        setItem: (k: string, v: string) => void data.set(k, v),
        removeItem: (k: string) => void data.delete(k),
        size: () => data.size,
    }
}

const stores = (): TokenStores & {app: ReturnType<typeof fakeStore>; session: ReturnType<typeof fakeStore>} => ({
    app: fakeStore(),
    session: fakeStore(),
})

describe('sessionToken', () => {
    it('legt Helfer-Sitzungen in die App-Ablage, nicht in die Sitzungsablage', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(s.session.size()).toBe(0)
        expect(readSessionToken(true, 1_000, s)).toBe('abc')
    })

    it('legt Verwaltungssitzungen unverändert in die Sitzungsablage', () => {
        const s = stores()
        writeSessionToken('abc', false, 1_000, s)
        expect(s.app.size()).toBe(0)
        expect(readSessionToken(false, 1_000, s)).toBe('abc')
    })

    it('gibt einen Helfer-Token innerhalb von sechs Stunden zurück', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(readSessionToken(true, 1_000 + SESSION_MAX_AGE_MS - 1, s)).toBe('abc')
    })

    it('verwirft einen Helfer-Token nach sechs Stunden und räumt ihn weg', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(readSessionToken(true, 1_000 + SESSION_MAX_AGE_MS + 1, s)).toBeNull()
        expect(s.app.size()).toBe(0)
    })

    it('schiebt die Frist mit jedem Auffrischen nach vorn', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        touchSessionToken(1_000 + SESSION_MAX_AGE_MS - 1, s)
        expect(readSessionToken(true, 1_000 + 2 * SESSION_MAX_AGE_MS - 2, s)).toBe('abc')
    })

    it('verwirft kaputten Inhalt, statt zu werfen', () => {
        const s = stores()
        s.app.setItem('session.app', '{kein json')
        expect(readSessionToken(true, 1_000, s)).toBeNull()
        expect(s.app.size()).toBe(0)
    })

    it('löscht beim Abmelden in der App beide Ablagen', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        writeSessionToken('def', false, 1_000, s)
        clearSessionToken(true, s)
        expect(s.app.size()).toBe(0)
        expect(s.session.size()).toBe(0)
    })

    it('lässt die Helfer-Ablage stehen, wenn sich die Verwaltung abmeldet', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        writeSessionToken('def', false, 1_000, s)
        clearSessionToken(false, s)
        expect(readSessionToken(true, 1_000, s)).toBe('abc')
        expect(s.session.size()).toBe(0)
    })

    it('fällt für Helfer auf eine bestehende Sitzungsablage zurück', () => {
        const s = stores()
        s.session.setItem('session', 'alt')
        expect(readSessionToken(true, 1_000, s)).toBe('alt')
    })

    it('legt die Nutzerangaben der Helfer-Sitzung mit ab', () => {
        const s = stores()
        const dto = {id: 'u1', privileges: []}
        writeSessionUser(dto, true, s)
        expect(readSessionUser(true, s)).toEqual(dto)
    })

    it('legt für die Verwaltung keine Nutzerangaben ab', () => {
        const s = stores()
        writeSessionUser({id: 'u1', privileges: []}, false, s)
        expect(s.app.size()).toBe(0)
        expect(readSessionUser(false, s)).toBeNull()
    })

    it('verwirft kaputte Nutzerangaben, statt zu werfen', () => {
        const s = stores()
        s.app.setItem('session.user', '{kein json')
        expect(readSessionUser(true, s)).toBeNull()
    })

    it('nimmt die Nutzerangaben beim Abmelden in der App mit', () => {
        const s = stores()
        writeSessionUser({id: 'u1', privileges: []}, true, s)
        clearSessionToken(true, s)
        expect(readSessionUser(true, s)).toBeNull()
    })
})
