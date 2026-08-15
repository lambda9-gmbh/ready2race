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

const match = (state: LiveDashboardMatchState, bye = false): CompetitionMatchDto => {
    const teams = bye ? [team()] : [team(), team()]
    return {
        id: crypto.randomUUID(),
        teams,
        weighting: 1,
        executionOrder: 1,
        activatedAt: null,
        skipped: false,
        status: {
            state,
            teamsTotal: teams.length,
            teamsScored: 0,
            teamsRaced: 0,
            teamsDeregistered: 0,
            bye: bye ? {cause: 'NO_OPPONENT', mustRace: false} : null,
        },
    }
}

describe('matchesOnDisplay', () => {
    it('zählt keinen Lauf, solange die Runde nur geplant ist', () => {
        expect(matchesOnDisplay([match('UPCOMING'), match('UNSCHEDULED')])).toBe(0)
    })

    it('zählt einen an den Start gerufenen Lauf', () => {
        expect(matchesOnDisplay([match('PREPARING'), match('UPCOMING')])).toBe(1)
    })

    it('zählt laufende, beendete und vollständig gewertete Läufe', () => {
        const matches = [match('RUNNING'), match('FINISHED'), match('AWAITING_FINISH')]

        expect(matchesOnDisplay(matches)).toBe(3)
    })

    /**
     * Ein abgesagter Lauf steht zwar im Zeitplan, aber seine Paarung hat niemand als Ereignis
     * gesehen — die öffentlichen Anzeigen blenden ihn aus.
     */
    it('zählt einen abgesagten Lauf nicht', () => {
        expect(matchesOnDisplay([match('SKIPPED')])).toBe(0)
    })

    /**
     * Der Fall, der die Warnung sonst dauerhaft auslösen würde: Ein Freilos trägt seinen Platz 1
     * seit der Erzeugung und gilt damit als vollständig gewertet, ohne je gefahren zu sein.
     * Erkannt wird es an `status.bye` aus dem Backend, nicht an einer eigenen Zählung.
     */
    it('zählt ein Freilos nicht, obwohl es als gewertet gilt', () => {
        expect(matchesOnDisplay([match('AWAITING_FINISH', true)])).toBe(0)
    })

    /**
     * Ein einzelnes Boot, das das Backend NICHT als Freilos führt (etwa ein Zeitfahren in einer
     * erforderlichen Runde), zählt mit — dort wird gefahren, und die Anzeige zeigt es.
     */
    it('zählt ein einzelnes Boot mit, das kein Freilos ist', () => {
        const single = match('RUNNING')
        single.teams = [team()]
        single.status.teamsTotal = 1

        expect(matchesOnDisplay([single])).toBe(1)
    })
})
