import {describe, expect, it} from 'vitest'
import {byeExplanation} from './matchBye.ts'

describe('byeExplanation', () => {
    it('schweigt ohne Freilos', () => {
        expect(byeExplanation(null)).toBeNull()
        expect(byeExplanation(undefined)).toBeNull()
    })

    it('nennt bei einer Abmeldung Mannschaft und Grund', () => {
        expect(
            byeExplanation({cause: 'DEREGISTRATION', teamName: 'RV Hansa', reason: 'Krankheit'}),
        ).toEqual({
            key: 'event.match.bye.deregistrationWithReason',
            values: {team: 'RV Hansa', reason: 'Krankheit'},
        })
    })

    it('nennt die Mannschaft auch ohne gespeicherten Grund', () => {
        expect(byeExplanation({cause: 'DEREGISTRATION', teamName: 'RV Hansa'})).toEqual({
            key: 'event.match.bye.deregistration',
            values: {team: 'RV Hansa'},
        })
    })

    it('bleibt beim neutralen Satz, wenn kein Gegner benannt ist', () => {
        expect(byeExplanation({cause: 'NO_OPPONENT'})).toEqual({
            key: 'event.match.bye.noOpponent',
        })
    })

    /** Eine Abmeldung ohne Namen ist keine belegte Ursache - dann lieber der neutrale Satz. */
    it('fällt ohne Mannschaftsnamen auf den neutralen Satz zurück', () => {
        expect(byeExplanation({cause: 'DEREGISTRATION', reason: 'Krankheit'})).toEqual({
            key: 'event.match.bye.noOpponent',
        })
    })
})
