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

const stores = (): TokenStores & {
    local: ReturnType<typeof fakeStore>
    session: ReturnType<typeof fakeStore>
} => ({
    local: fakeStore(),
    session: fakeStore(),
})

describe('sessionToken', () => {
    it('legt Helfer-Sitzungen in die dauerhafte Ablage, nicht in die Sitzungsablage', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(s.session.size()).toBe(0)
        expect(readSessionToken(true, 1_000, s)).toBe('abc')
    })

    it('legt Verwaltungssitzungen ebenfalls in die dauerhafte Ablage', () => {
        const s = stores()
        writeSessionToken('abc', false, 1_000, s)
        expect(s.session.size()).toBe(0)
        expect(readSessionToken(false, 1_000, s)).toBe('abc')
    })

    it('hält Helfer- und Verwaltungssitzung unter getrennten Schlüsseln auseinander', () => {
        const s = stores()
        writeSessionToken('helfer', true, 1_000, s)
        writeSessionToken('verwaltung', false, 1_000, s)
        expect(readSessionToken(true, 1_000, s)).toBe('helfer')
        expect(readSessionToken(false, 1_000, s)).toBe('verwaltung')
    })

    it('lässt die Verwaltungssitzung einen neuen Tab überleben', () => {
        // Neuer Tab = gleiche localStorage-Instanz, aber frisches sessionStorage.
        const ersterTab = stores()
        writeSessionToken('abc', false, 1_000, ersterTab)
        const neuerTab: TokenStores = {local: ersterTab.local, session: fakeStore()}
        expect(readSessionToken(false, 2_000, neuerTab)).toBe('abc')
    })

    it('gibt einen Helfer-Token innerhalb von sechs Stunden zurück', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(readSessionToken(true, 1_000 + SESSION_MAX_AGE_MS - 1, s)).toBe('abc')
    })

    it('gibt einen Verwaltungs-Token innerhalb von sechs Stunden zurück', () => {
        const s = stores()
        writeSessionToken('abc', false, 1_000, s)
        expect(readSessionToken(false, 1_000 + SESSION_MAX_AGE_MS - 1, s)).toBe('abc')
    })

    it('verwirft einen Helfer-Token nach sechs Stunden und räumt ihn weg', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        expect(readSessionToken(true, 1_000 + SESSION_MAX_AGE_MS + 1, s)).toBeNull()
        expect(s.local.size()).toBe(0)
    })

    it('verwirft einen Verwaltungs-Token nach sechs Stunden und räumt ihn weg', () => {
        const s = stores()
        writeSessionToken('abc', false, 1_000, s)
        expect(readSessionToken(false, 1_000 + SESSION_MAX_AGE_MS + 1, s)).toBeNull()
        expect(s.local.size()).toBe(0)
    })

    it('schiebt die Frist der Helfer-Sitzung mit jedem Auffrischen nach vorn', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        touchSessionToken(1_000 + SESSION_MAX_AGE_MS - 1, s)
        expect(readSessionToken(true, 1_000 + 2 * SESSION_MAX_AGE_MS - 2, s)).toBe('abc')
    })

    it('schiebt die Frist der Verwaltungssitzung mit jedem Auffrischen nach vorn', () => {
        const s = stores()
        writeSessionToken('abc', false, 1_000, s)
        touchSessionToken(1_000 + SESSION_MAX_AGE_MS - 1, s)
        expect(readSessionToken(false, 1_000 + 2 * SESSION_MAX_AGE_MS - 2, s)).toBe('abc')
    })

    it('schiebt beim Auffrischen beide Ablagen nach vorn', () => {
        const s = stores()
        writeSessionToken('helfer', true, 1_000, s)
        writeSessionToken('verwaltung', false, 1_000, s)
        touchSessionToken(1_000 + SESSION_MAX_AGE_MS - 1, s)
        const später = 1_000 + 2 * SESSION_MAX_AGE_MS - 2
        expect(readSessionToken(true, später, s)).toBe('helfer')
        expect(readSessionToken(false, später, s)).toBe('verwaltung')
    })

    it('verwirft kaputten Inhalt, statt zu werfen', () => {
        const s = stores()
        s.local.setItem('session.app', '{kein json')
        expect(readSessionToken(true, 1_000, s)).toBeNull()
        expect(s.local.size()).toBe(0)
    })

    it('verwirft kaputten Verwaltungs-Inhalt, statt zu werfen', () => {
        const s = stores()
        s.local.setItem('session.admin', '{kein json')
        expect(readSessionToken(false, 1_000, s)).toBeNull()
        expect(s.local.size()).toBe(0)
    })

    it('übernimmt einen alten Verwaltungs-Token aus der Sitzungsablage und räumt sie', () => {
        // Übergang nach dem Deploy: Wer noch mit sessionStorage angemeldet ist, bleibt es.
        const s = stores()
        s.session.setItem('session', 'alt')
        expect(readSessionToken(false, 1_000, s)).toBe('alt')
        expect(s.session.size()).toBe(0)
        // Ab jetzt liegt der Token in der dauerhaften Ablage - mit frisch gesetzter Frist.
        expect(readSessionToken(false, 1_000 + SESSION_MAX_AGE_MS - 1, {local: s.local, session: fakeStore()})).toBe(
            'alt',
        )
    })

    it('bevorzugt die dauerhafte Ablage vor einem alten Sitzungs-Token', () => {
        const s = stores()
        writeSessionToken('neu', false, 1_000, s)
        s.session.setItem('session', 'alt')
        expect(readSessionToken(false, 1_000, s)).toBe('neu')
    })

    it('löscht beim Abmelden in der App die Helfer-Ablage samt Altbestand', () => {
        const s = stores()
        writeSessionToken('abc', true, 1_000, s)
        s.session.setItem('session', 'alt')
        clearSessionToken(true, s)
        expect(readSessionToken(true, 1_000, s)).toBeNull()
        expect(s.session.size()).toBe(0)
    })

    it('lässt die Verwaltungs-Ablage stehen, wenn sich die App abmeldet', () => {
        const s = stores()
        writeSessionToken('helfer', true, 1_000, s)
        writeSessionToken('verwaltung', false, 1_000, s)
        clearSessionToken(true, s)
        expect(readSessionToken(true, 1_000, s)).toBeNull()
        expect(readSessionToken(false, 1_000, s)).toBe('verwaltung')
    })

    it('lässt die Helfer-Ablage stehen, wenn sich die Verwaltung abmeldet', () => {
        const s = stores()
        writeSessionToken('helfer', true, 1_000, s)
        writeSessionToken('verwaltung', false, 1_000, s)
        s.session.setItem('session', 'alt')
        clearSessionToken(false, s)
        expect(readSessionToken(false, 1_000, s)).toBeNull()
        expect(readSessionToken(true, 1_000, s)).toBe('helfer')
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
        expect(s.local.size()).toBe(0)
        expect(readSessionUser(false, s)).toBeNull()
    })

    it('verwirft kaputte Nutzerangaben, statt zu werfen', () => {
        const s = stores()
        s.local.setItem('session.user', '{kein json')
        expect(readSessionUser(true, s)).toBeNull()
    })

    it('nimmt die Nutzerangaben beim Abmelden in der App mit', () => {
        const s = stores()
        writeSessionUser({id: 'u1', privileges: []}, true, s)
        clearSessionToken(true, s)
        expect(readSessionUser(true, s)).toBeNull()
    })
})
