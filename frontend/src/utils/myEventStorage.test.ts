import {beforeEach, describe, expect, it} from 'vitest'
import {
    codesForEvent,
    forgetMyEventCode,
    MY_EVENT_STORAGE_KEY,
    readMyEventCodes,
    rememberMyEventCode,
} from './myEventStorage.ts'

const eventA = '11111111-1111-1111-1111-111111111111'
const eventB = '22222222-2222-2222-2222-222222222222'

// Das Projekt hat keine DOM-Testumgebung (kein jsdom/happy-dom), darum gibt es kein
// eingebautes localStorage. Fuer die Tests reicht eine minimale In-Memory-Nachbildung —
// die Haertung im Modul selbst wird ueber die beiden Faelle unten geprueft, in denen
// genau dieser Speicher fehlt oder wirft.
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

describe('myEventStorage', () => {
    beforeEach(() => {
        globalThis.localStorage = createFakeLocalStorage() as Storage
    })

    it('merkt einen Code und liest ihn zurueck', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        expect(readMyEventCodes()).toEqual([{qrCode: 'abc', eventId: eventA}])
    })

    it('haelt mehrere Codes nebeneinander', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'def', eventId: eventA})
        expect(readMyEventCodes()).toHaveLength(2)
    })

    it('ueberschreibt denselben Code statt ihn zu doppeln', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'abc', eventId: eventA, displayName: 'Ilka Heller'})
        expect(readMyEventCodes()).toEqual([
            {qrCode: 'abc', eventId: eventA, displayName: 'Ilka Heller'},
        ])
    })

    it('filtert nach Veranstaltung', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'def', eventId: eventB})
        expect(codesForEvent(eventA).map(c => c.qrCode)).toEqual(['abc'])
    })

    it('entfernt einen einzelnen Code', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'def', eventId: eventA})
        forgetMyEventCode('abc')
        expect(readMyEventCodes().map(c => c.qrCode)).toEqual(['def'])
    })

    it('liefert eine leere Liste bei kaputtem Speicherinhalt', () => {
        // Ein von Hand verbogener oder von einer aelteren Version geschriebener Eintrag
        // darf die Ergebnisseite nicht zerlegen.
        localStorage.setItem(MY_EVENT_STORAGE_KEY, '{kein json')
        expect(readMyEventCodes()).toEqual([])
    })

    it('verwirft Eintraege ohne Pflichtfelder', () => {
        localStorage.setItem(MY_EVENT_STORAGE_KEY, JSON.stringify([{qrCode: 'abc'}, 42]))
        expect(readMyEventCodes()).toEqual([])
    })

    it('liefert eine leere Liste, wenn localStorage gar nicht existiert', () => {
        // @ts-expect-error Simuliert ein Geraet/Umgebung ohne localStorage.
        delete globalThis.localStorage
        expect(readMyEventCodes()).toEqual([])
        expect(() => rememberMyEventCode({qrCode: 'abc', eventId: eventA})).not.toThrow()
    })

    it('wirft nicht, wenn localStorage.setItem wirft (voller Speicher, privater Modus)', () => {
        localStorage.setItem = () => {
            throw new Error('QuotaExceededError')
        }
        expect(() => rememberMyEventCode({qrCode: 'abc', eventId: eventA})).not.toThrow()
    })
})
