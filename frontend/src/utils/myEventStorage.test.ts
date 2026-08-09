import {beforeEach, describe, expect, it} from 'vitest'
import {
    activeCodeForEvent,
    codesForEvent,
    forgetMyEventCode,
    MY_EVENT_STORAGE_KEY,
    readMyEventCodes,
    rememberMyEventCode,
    rememberMyEventDisplayName,
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
        expect(readMyEventCodes()).toEqual([
            {qrCode: 'abc', eventId: eventA, lastSeenAt: expect.any(Number)},
        ])
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
            {
                qrCode: 'abc',
                eventId: eventA,
                displayName: 'Ilka Heller',
                lastSeenAt: expect.any(Number),
            },
        ])
    })

    it('behaelt den bekannten Namen, wenn derselbe Code erneut gescannt wird', () => {
        // Der Scan selbst kennt den Namen nicht — er darf ihn nicht wieder ausradieren.
        rememberMyEventCode({qrCode: 'abc', eventId: eventA, displayName: 'Ilka Heller'})
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        expect(readMyEventCodes()[0].displayName).toBe('Ilka Heller')
    })

    it('filtert nach Veranstaltung', () => {
        rememberMyEventCode({qrCode: 'abc', eventId: eventA})
        rememberMyEventCode({qrCode: 'def', eventId: eventB})
        expect(codesForEvent(eventA).map(c => c.qrCode)).toEqual(['abc'])
    })

    // Die Reihenfolge der Liste und die Auswahl der angezeigten Person sind zwei
    // verschiedene Fragen. Vorher trug die Reihenfolge beides — dadurch zeigte das zweite
    // gescannte Band die erste Person, und der Umschalter sortierte sich beim Nachtragen
    // des Namens unter dem Finger um.
    describe('Reihenfolge und Auswahl', () => {
        it('haelt die Reihenfolge des ersten Scans, auch beim erneuten Scan', () => {
            rememberMyEventCode({qrCode: 'abc', eventId: eventA})
            rememberMyEventCode({qrCode: 'def', eventId: eventA})
            rememberMyEventCode({qrCode: 'abc', eventId: eventA})
            expect(codesForEvent(eventA).map(c => c.qrCode)).toEqual(['abc', 'def'])
        })

        it('waehlt den zuletzt gescannten Code aus', () => {
            rememberMyEventCode({qrCode: 'abc', eventId: eventA})
            rememberMyEventCode({qrCode: 'def', eventId: eventA})
            expect(activeCodeForEvent(eventA)?.qrCode).toBe('def')
        })

        it('waehlt auch dann den zuletzt gescannten Code, wenn er schon in der Liste stand', () => {
            rememberMyEventCode({qrCode: 'abc', eventId: eventA, lastSeenAt: 1000})
            rememberMyEventCode({qrCode: 'def', eventId: eventA, lastSeenAt: 2000})
            rememberMyEventCode({qrCode: 'abc', eventId: eventA, lastSeenAt: 3000})
            expect(codesForEvent(eventA).map(c => c.qrCode)).toEqual(['abc', 'def'])
            expect(activeCodeForEvent(eventA)?.qrCode).toBe('abc')
        })

        it('beachtet nur Codes der eigenen Veranstaltung', () => {
            rememberMyEventCode({qrCode: 'abc', eventId: eventA, lastSeenAt: 1000})
            rememberMyEventCode({qrCode: 'def', eventId: eventB, lastSeenAt: 2000})
            expect(activeCodeForEvent(eventA)?.qrCode).toBe('abc')
        })

        it('liefert nichts, wenn fuer die Veranstaltung kein Code hinterlegt ist', () => {
            rememberMyEventCode({qrCode: 'abc', eventId: eventB})
            expect(activeCodeForEvent(eventA)).toBeUndefined()
        })

        it('waehlt Alteintraege ohne Zeitstempel in Listenreihenfolge', () => {
            localStorage.setItem(
                MY_EVENT_STORAGE_KEY,
                JSON.stringify([
                    {qrCode: 'abc', eventId: eventA},
                    {qrCode: 'def', eventId: eventA},
                ]),
            )
            expect(activeCodeForEvent(eventA)?.qrCode).toBe('def')
        })

        it('traegt den Namen nach, ohne Position und Auswahl zu veraendern', () => {
            rememberMyEventCode({qrCode: 'abc', eventId: eventA, lastSeenAt: 1000})
            rememberMyEventCode({qrCode: 'def', eventId: eventA, lastSeenAt: 2000})
            rememberMyEventDisplayName('abc', 'Nina Nordmann')
            expect(codesForEvent(eventA)).toEqual([
                {qrCode: 'abc', eventId: eventA, lastSeenAt: 1000, displayName: 'Nina Nordmann'},
                {qrCode: 'def', eventId: eventA, lastSeenAt: 2000},
            ])
            expect(activeCodeForEvent(eventA)?.qrCode).toBe('def')
        })

        it('ignoriert einen Namen fuer einen unbekannten Code', () => {
            rememberMyEventDisplayName('abc', 'Nina Nordmann')
            expect(readMyEventCodes()).toEqual([])
        })
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
