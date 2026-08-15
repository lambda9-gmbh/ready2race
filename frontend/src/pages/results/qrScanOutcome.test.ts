import {describe, expect, it} from 'vitest'
import {qrScanOutcome} from './qrScanOutcome.ts'

describe('qrScanOutcome', () => {
    it('erkennt ein noch nicht verknüpftes Band an der leeren 204-Antwort', () => {
        expect(qrScanOutcome({data: undefined, error: undefined, status: 204})).toEqual({
            kind: 'unlinked',
        })
    })

    it('behandelt auch eine leere 200-Antwort als unverknüpft, nicht als Fehler', () => {
        // Sollte ein Client den leeren Body je anders melden, darf daraus kein Fehler werden.
        expect(qrScanOutcome({data: undefined, error: undefined, status: 200})).toEqual({
            kind: 'unlinked',
        })
    })

    it('bleibt bei echten Fehlerantworten beim Fehler', () => {
        expect(qrScanOutcome({data: undefined, error: {message: 'kaputt'}, status: 500})).toEqual({
            kind: 'error',
        })
        expect(qrScanOutcome({data: undefined, error: {message: 'weg'}, status: 404})).toEqual({
            kind: 'error',
        })
    })

    it('führt ein Helferband (User) auf die Ergebnisseite', () => {
        expect(
            qrScanOutcome({
                data: {eventId: 'e-1', type: 'User'},
                error: undefined,
                status: 200,
            }),
        ).toEqual({kind: 'user', eventId: 'e-1'})
    })

    it('führt ein Teilnahmeband zu „Mein Event"', () => {
        expect(
            qrScanOutcome({
                data: {eventId: 'e-1', type: 'Participant'},
                error: undefined,
                status: 200,
            }),
        ).toEqual({kind: 'participant', eventId: 'e-1'})
    })

    it('behandelt eine Antwort ohne type wie ein Teilnahmeband (bisheriges Verhalten)', () => {
        expect(qrScanOutcome({data: {eventId: 'e-1'}, error: undefined, status: 200})).toEqual({
            kind: 'participant',
            eventId: 'e-1',
        })
    })
})
