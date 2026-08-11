import {describe, expect, it} from 'vitest'
import {raceClockerPollStatus} from './raceClockerPollStatus.ts'

describe('raceClockerPollStatus', () => {
    it('meldet nichts, solange nie abgerufen wurde', () => {
        expect(
            raceClockerPollStatus({
                raceClockerPolledAt: null,
                raceClockerPollError: null,
                raceClockerAutoPausedAt: null,
            }).kind,
        ).toBe('none')
    })

    it('meldet den letzten erfolgreichen Abruf', () => {
        expect(
            raceClockerPollStatus({
                raceClockerPolledAt: '2026-08-14T10:03:12',
                raceClockerPollError: null,
                raceClockerAutoPausedAt: null,
            }).kind,
        ).toBe('ok')
    })

    it('meldet den letzten Fehler mit übersetzbarem Schlüssel', () => {
        const status = raceClockerPollStatus({
            raceClockerPolledAt: '2026-08-14T10:03:12',
            raceClockerPollError: 'RACECLOCKER_UNREACHABLE',
            raceClockerAutoPausedAt: null,
        })

        expect(status.kind).toBe('error')
        expect(status.errorKey).toBeDefined()
    })

    // Pausiert schlägt alles: Der letzte Fehler stammt aus der Zeit davor und ist keine Aussage
    // mehr über einen Lauf, den der Job gar nicht mehr anfasst.
    it('meldet Pause vor Fehler', () => {
        expect(
            raceClockerPollStatus({
                raceClockerPolledAt: '2026-08-14T10:03:12',
                raceClockerPollError: 'RACECLOCKER_UNREACHABLE',
                raceClockerAutoPausedAt: '2026-08-14T10:05:00',
            }).kind,
        ).toBe('paused')
    })

    it('meldet für einen unbekannten Fehlercode trotzdem einen Fehler', () => {
        const status = raceClockerPollStatus({
            raceClockerPolledAt: '2026-08-14T10:03:12',
            raceClockerPollError: 'SOMETHING_NEW',
            raceClockerAutoPausedAt: null,
        })

        expect(status.kind).toBe('error')
        expect(status.errorKey).toBeUndefined()
    })

    it('nutzt für RACECLOCKER_DUPLICATE_TEAMS den Abruf-spezifischen Text, nicht den Knopf-Text', () => {
        const status = raceClockerPollStatus({
            raceClockerPolledAt: '2026-08-14T10:03:12',
            raceClockerPollError: 'RACECLOCKER_DUPLICATE_TEAMS',
            raceClockerAutoPausedAt: null,
        })

        expect(status.kind).toBe('error')
        expect(status.errorKey).toBeDefined()
        expect(status.errorKey).not.toBe('event.competition.execution.results.raceclocker.error.duplicateTeams')
    })
})
