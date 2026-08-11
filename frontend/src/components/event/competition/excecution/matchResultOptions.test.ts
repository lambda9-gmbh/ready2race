import {describe, expect, it} from 'vitest'
import {matchResultOptions} from './matchResultOptions.ts'

describe('matchResultOptions', () => {
    it('zeigt bei RaceClocker alle Wege — der xlsx-Upload bleibt der Notausgang', () => {
        expect(matchResultOptions('RACECLOCKER')).toEqual(['form', 'XLS', 'RACECLOCKER', 'RACECLOCKER_FILE'])
    })

    it('verbirgt bei Webscorer das Abholen aus RaceClocker', () => {
        expect(matchResultOptions('WEBSCORER')).toEqual(['form', 'XLS'])
    })

    it('lässt ohne gesetztes System alles stehen wie bisher', () => {
        // Bestehende Wettkämpfe haben kein System; ihnen darf nichts wegfallen.
        expect(matchResultOptions('NONE')).toEqual(['form', 'XLS', 'RACECLOCKER', 'RACECLOCKER_FILE'])
    })
})
