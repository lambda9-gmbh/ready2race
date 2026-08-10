import {describe, expect, it} from 'vitest'
import {
    CompetitionMatchDto,
    CompetitionMatchTeamDto,
    LiveDashboardMatchState,
} from '@api/types.gen.ts'
import {matchesOnDisplay} from './roundDeletion.ts'

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

const match = (state: LiveDashboardMatchState, teamCount = 2): CompetitionMatchDto => {
    const teams = Array.from({length: teamCount}, () => team())
    return {
        id: crypto.randomUUID(),
        teams,
        weighting: 1,
        executionOrder: 1,
        activatedAt: null,
        skipped: false,
        status: {state, teamsTotal: teams.length, teamsScored: 0},
    }
}

describe('matchesOnDisplay', () => {
    it('zählt keinen Lauf, solange die Runde nur geplant ist', () => {
        expect(matchesOnDisplay([match('UPCOMING'), match('UNSCHEDULED')], true)).toBe(0)
    })

    it('zählt einen an den Start gerufenen Lauf', () => {
        expect(matchesOnDisplay([match('PREPARING'), match('UPCOMING')], true)).toBe(1)
    })

    it('zählt laufende, beendete und vollständig gewertete Läufe', () => {
        const matches = [match('RUNNING'), match('FINISHED'), match('AWAITING_FINISH')]

        expect(matchesOnDisplay(matches, true)).toBe(3)
    })

    /**
     * Ein abgesagter Lauf steht zwar im Zeitplan, aber seine Paarung hat niemand als Ereignis
     * gesehen — die öffentlichen Anzeigen blenden ihn aus.
     */
    it('zählt einen abgesagten Lauf nicht', () => {
        expect(matchesOnDisplay([match('SKIPPED')], true)).toBe(0)
    })

    /**
     * Der Fall, der die Warnung sonst dauerhaft auslösen würde: Ein Freilos trägt seinen Platz 1
     * seit der Erzeugung und gilt damit als vollständig gewertet, ohne je gefahren zu sein.
     */
    it('zählt ein Freilos nicht, obwohl es als gewertet gilt', () => {
        expect(matchesOnDisplay([match('AWAITING_FINISH', 1)], false)).toBe(0)
    })

    /**
     * In einer erforderlichen Runde ist ein einzelnes Boot kein Freilos, sondern ein Zeitfahren —
     * dort wird gefahren, und die Anzeige zeigt es.
     */
    it('zählt ein einzelnes Boot in einer erforderlichen Runde mit', () => {
        expect(matchesOnDisplay([match('RUNNING', 1)], true)).toBe(1)
    })
})
