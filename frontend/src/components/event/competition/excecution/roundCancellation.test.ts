import {describe, expect, it} from 'vitest'
import {CompetitionMatchDto, CompetitionMatchTeamDto} from '@api/types.gen.ts'
import {roundHasNothingToRace} from './roundCancellation.ts'

const team = (overrides: Partial<CompetitionMatchTeamDto> = {}): CompetitionMatchTeamDto => ({
    registrationId: crypto.randomUUID(),
    teamNumber: 1,
    clubId: crypto.randomUUID(),
    clubName: 'Ruderclub Musterstadt',
    namedParticipants: [],
    startNumber: 1,
    deregistered: false,
    failed: false,
    ...overrides,
})

const match = (teams: CompetitionMatchTeamDto[]): CompetitionMatchDto => ({
    id: crypto.randomUUID(),
    teams,
    weighting: 1,
    executionOrder: 1,
    currentlyRunning: false,
})

describe('roundHasNothingToRace', () => {
    it('ist false für eine Runde ohne Läufe (nicht materialisiert)', () => {
        expect(roundHasNothingToRace([])).toBe(false)
    })

    it('ist true, wenn alle Läufe Freilose sind (nur ein Team)', () => {
        const matches = [match([team()]), match([team()])]

        expect(roundHasNothingToRace(matches)).toBe(true)
    })

    it('ist false, sobald ein Lauf zwei fahrende Teams hat', () => {
        const matches = [match([team(), team()])]

        expect(roundHasNothingToRace(matches)).toBe(false)
    })

    it('ist true, wenn von zwei Teams eines abgemeldet ist', () => {
        const matches = [match([team(), team({deregistered: true})])]

        expect(roundHasNothingToRace(matches)).toBe(true)
    })

    it('ist false bei einer Mischung aus Freilosen und einem noch zu fahrenden Lauf', () => {
        const matches = [match([team()]), match([team(), team()]), match([team()])]

        expect(roundHasNothingToRace(matches)).toBe(false)
    })
})
