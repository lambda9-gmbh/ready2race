import {describe, expect, it} from 'vitest'
import {CompetitionMatchDto, CompetitionMatchTeamDto} from '@api/types.gen.ts'
import {emptyEditMatchForm, mapMatchDtoToEditMatchForm} from './editMatchForm.ts'

const team = (startNumber: number): CompetitionMatchTeamDto => ({
    registrationId: `registration-${startNumber}`,
    teamNumber: startNumber,
    clubId: crypto.randomUUID(),
    clubName: 'Ruderclub Musterstadt',
    namedParticipants: [],
    startNumber,
    deregistered: false,
    failed: false,
    bye: false,
})

const match = (overrides: Partial<CompetitionMatchDto>): CompetitionMatchDto => ({
    id: crypto.randomUUID(),
    teams: [],
    weighting: 1,
    executionOrder: 1,
    activatedAt: null,
    skipped: false,
    status: {state: 'UPCOMING', teamsTotal: 0, teamsScored: 0, teamsRaced: 0, teamsDeregistered: 0},
    ...overrides,
})

describe('mapMatchDtoToEditMatchForm', () => {
    it('übernimmt die bestehende Startzeit des Laufs', () => {
        const form = mapMatchDtoToEditMatchForm(match({startTime: '2026-08-14T10:30:00'}))

        expect(form.startTime).toBe('2026-08-14T10:30:00')
    })

    it('lässt die Startzeit leer, wenn der Lauf keine hat', () => {
        const form = mapMatchDtoToEditMatchForm(match({}))

        expect(form.startTime).toBe('')
    })

    it('belegt jedes Feld des Formulars, damit reset() keines verwirft', () => {
        const form = mapMatchDtoToEditMatchForm(match({startTime: '2026-08-14T10:30:00'}))

        expect(Object.keys(form).sort()).toEqual(Object.keys(emptyEditMatchForm).sort())
    })

    it('sortiert die Boote nach Startnummer', () => {
        const form = mapMatchDtoToEditMatchForm(match({teams: [team(3), team(1), team(2)]}))

        expect(form.teams.map(t => t.startNumber)).toEqual(['1', '2', '3'])
    })

    it('lässt den ausgewählten Lauf unverändert für den Absenden-Pfad', () => {
        const dto = match({startTime: '2026-08-14T10:30:00'})

        expect(mapMatchDtoToEditMatchForm(dto).selectedMatchDto).toBe(dto)
    })
})
