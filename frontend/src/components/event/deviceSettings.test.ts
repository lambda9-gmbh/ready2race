import {beforeEach, describe, expect, it, vi} from 'vitest'
import {setStoredFlag, storedFlag} from './deviceSettings.ts'

// Das Projekt hat keine DOM-Testumgebung (kein jsdom/happy-dom), darum gibt es kein
// eingebautes localStorage und kein window. Für die Tests reicht eine minimale
// In-Memory-Nachbildung — dasselbe Muster wie in myEventStorage.test.ts.
const createFakeLocalStorage = () => {
    let store = new Map<string, string>()
    return {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => {
            store.set(key, value)
        },
        removeItem: (key: string) => {
            store.delete(key)
        },
        clear: () => {
            store = new Map<string, string>()
        },
    }
}

describe('deviceSettings', () => {
    const dispatched = vi.fn()

    beforeEach(() => {
        globalThis.localStorage = createFakeLocalStorage() as Storage
        dispatched.mockClear()
        // Nur der eine Aufruf, den setStoredFlag braucht — mehr Fenster gibt es hier nicht.
        globalThis.window = {dispatchEvent: dispatched} as unknown as Window & typeof globalThis
    })

    it('liefert die Voreinstellung, solange nichts gespeichert ist', () => {
        expect(storedFlag('irgendein_schalter', true)).toBe(true)
        expect(storedFlag('irgendein_schalter', false)).toBe(false)
    })

    it('liest den gespeicherten Wert statt der Voreinstellung', () => {
        setStoredFlag('irgendein_schalter', true)
        expect(storedFlag('irgendein_schalter', false)).toBe(true)

        setStoredFlag('irgendein_schalter', false)
        expect(storedFlag('irgendein_schalter', true)).toBe(false)
    })

    it('hält verschiedene Schlüssel auseinander', () => {
        setStoredFlag('schalter_a', true)
        expect(storedFlag('schalter_b', false)).toBe(false)
    })

    it('verteilt jede Änderung als Fenster-Ereignis im selben Tab', () => {
        setStoredFlag('irgendein_schalter', true)
        expect(dispatched).toHaveBeenCalledTimes(1)
    })

    it('fällt auf die Voreinstellung zurück, wenn localStorage fehlt', () => {
        // @ts-expect-error Simuliert ein Gerät/Umgebung ohne localStorage.
        delete globalThis.localStorage
        expect(storedFlag('irgendein_schalter', true)).toBe(true)
    })

    it('wirft nicht, wenn localStorage.setItem wirft (voller Speicher, privater Modus)', () => {
        localStorage.setItem = () => {
            throw new Error('QuotaExceededError')
        }
        expect(() => setStoredFlag('irgendein_schalter', true)).not.toThrow()
        // Ohne neuen Wert gibt es auch nichts zu verteilen.
        expect(dispatched).not.toHaveBeenCalled()
    })
})
