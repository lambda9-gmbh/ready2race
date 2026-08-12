import {describe, expect, it} from 'vitest'
import {byeExplanation} from './matchBye.ts'

describe('byeExplanation', () => {
    it('schweigt ohne Freilos', () => {
        expect(byeExplanation(null)).toBeNull()
        expect(byeExplanation(undefined)).toBeNull()
    })

    it('nennt bei einer Abmeldung Mannschaft und Grund', () => {
        expect(
            byeExplanation({
                cause: 'DEREGISTRATION',
                teamName: 'RV Hansa',
                reason: 'Krankheit',
                mustRace: false,
            }),
        ).toEqual({
            key: 'event.match.bye.deregistrationWithReason',
            values: {team: 'RV Hansa', reason: 'Krankheit', seed: ''},
        })
    })

    it('nennt die Mannschaft auch ohne gespeicherten Grund', () => {
        expect(
            byeExplanation({cause: 'DEREGISTRATION', teamName: 'RV Hansa', mustRace: false}),
        ).toEqual({
            key: 'event.match.bye.deregistration',
            values: {team: 'RV Hansa', seed: ''},
        })
    })

    it('bleibt beim neutralen Satz, wenn kein Gegner benannt ist', () => {
        expect(byeExplanation({cause: 'NO_OPPONENT', mustRace: false})).toEqual({
            key: 'event.match.bye.noOpponent',
            values: {seed: ''},
        })
    })

    /** Eine Abmeldung ohne Namen ist keine belegte Ursache - dann lieber der neutrale Satz. */
    it('fällt ohne Mannschaftsnamen auf den neutralen Satz zurück', () => {
        expect(
            byeExplanation({cause: 'DEREGISTRATION', reason: 'Krankheit', mustRace: false}),
        ).toEqual({
            key: 'event.match.bye.noOpponent',
            values: {seed: ''},
        })
    })

    /** „Muss gefahren werden" reicht der Satz als Flag weiter - die Komponente hängt den Hinweis an. */
    it('reicht mustRace als Flag durch', () => {
        expect(byeExplanation({cause: 'NO_OPPONENT', mustRace: true})).toEqual({
            key: 'event.match.bye.noOpponent',
            values: {seed: ''},
            mustRace: true,
        })
    })

    /**
     * Die Setzungszahl wandert als {{seed}}-Wert in den Satz - mit führendem Leerzeichen im WERT,
     * damit „Freilos 1" entsteht und ohne Zahl kein doppeltes Leerzeichen im Schlüssel steht.
     */
    it('trägt die Setzungszahl als Interpolationswert', () => {
        expect(byeExplanation({cause: 'NO_OPPONENT', mustRace: false, seed: 1})).toEqual({
            key: 'event.match.bye.noOpponent',
            values: {seed: ' 1'},
        })
        expect(
            byeExplanation({
                cause: 'DEREGISTRATION',
                teamName: 'RV Hansa',
                mustRace: false,
                seed: 3,
            }),
        ).toEqual({
            key: 'event.match.bye.deregistration',
            values: {team: 'RV Hansa', seed: ' 3'},
        })
    })
})
