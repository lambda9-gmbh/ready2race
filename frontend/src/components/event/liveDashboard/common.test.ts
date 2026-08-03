import {describe, expect, it} from 'vitest'
import {LiveDashboardMatchDto, LiveDashboardTeamDto, PendingSlotDto} from '@api/types.gen.ts'
import {
    buildLiveDashboardTimeline,
    openResultTeams,
    pendingSlotLabel,
    teamHasResult,
    teamSeverity,
} from './common.ts'

const noRequirements = {
    total: 0,
    fulfilled: 0,
    missingRequired: 0,
    missingOptional: 0,
    timeIssues: 0,
}

const team = (overrides: Partial<LiveDashboardTeamDto>): LiveDashboardTeamDto => ({
    teamId: crypto.randomUUID(),
    failed: false,
    deregistered: false,
    invoiceState: 'NONE',
    requirements: noRequirements,
    substituted: false,
    ...overrides,
})

describe('teamHasResult', () => {
    it('zählt Platz, Zeit und Ausscheiden als Ergebnis', () => {
        expect(teamHasResult(team({place: 1}))).toBe(true)
        expect(teamHasResult(team({time: '07:12.340'}))).toBe(true)
        expect(teamHasResult(team({failed: true, failedReason: 'DNF'}))).toBe(true)
    })

    it('erwartet von abgemeldeten Booten kein Ergebnis', () => {
        expect(teamHasResult(team({deregistered: true}))).toBe(true)
    })

    it('erkennt ein offenes Boot', () => {
        expect(teamHasResult(team({}))).toBe(false)
    })
})

describe('teamSeverity', () => {
    it('meldet eine fehlende Pflichtbedingung als Fehler', () => {
        const severity = teamSeverity(
            team({requirements: {...noRequirements, total: 3, fulfilled: 2, missingRequired: 1}}),
        )
        expect(severity).toBe('error')
    })

    it('meldet eine offene Rechnung als Fehler', () => {
        expect(teamSeverity(team({invoiceState: 'OPEN'}))).toBe('error')
    })

    it('meldet eine Prüfung außerhalb des Zeitfensters als Warnung', () => {
        const severity = teamSeverity(
            team({requirements: {...noRequirements, total: 2, fulfilled: 2, timeIssues: 1}}),
        )
        expect(severity).toBe('warning')
    })

    it('wertet eine fehlende Pflichtbedingung schwerer als eine Zeitabweichung', () => {
        const severity = teamSeverity(
            team({
                requirements: {
                    ...noRequirements,
                    total: 3,
                    fulfilled: 2,
                    missingRequired: 1,
                    timeIssues: 1,
                },
            }),
        )
        expect(severity).toBe('error')
    })

    it('bleibt neutral, wenn ausschließlich Optionales fehlt', () => {
        const severity = teamSeverity(
            team({requirements: {...noRequirements, total: 1, missingOptional: 1}}),
        )
        expect(severity).toBe('neutral')
    })

    it('lässt eine fehlende optionale Bedingung eine erfüllte nicht abwerten', () => {
        const severity = teamSeverity(
            team({requirements: {...noRequirements, total: 2, fulfilled: 1, missingOptional: 1}}),
        )
        expect(severity).toBe('ok')
    })

    it('meldet vollständig erfüllte Bedingungen als in Ordnung', () => {
        const severity = teamSeverity(
            team({requirements: {...noRequirements, total: 3, fulfilled: 3}}),
        )
        expect(severity).toBe('ok')
    })

    it('bleibt ohne zugewiesene Bedingungen neutral', () => {
        expect(teamSeverity(team({}))).toBe('neutral')
    })
})

describe('openResultTeams', () => {
    it('liefert nur die Boote ohne Ergebnis', () => {
        const open = team({startNumber: 3})
        const teams = [
            team({startNumber: 1, place: 1}),
            team({startNumber: 2, deregistered: true}),
            open,
            team({startNumber: 4, failed: true}),
        ]

        expect(openResultTeams({teams})).toEqual([open])
    })

    it('liefert bei vollständigen Ergebnissen nichts', () => {
        expect(openResultTeams({teams: [team({place: 1}), team({place: 2})]})).toEqual([])
    })
})

const match = (overrides: Partial<LiveDashboardMatchDto>): LiveDashboardMatchDto => ({
    matchId: crypto.randomUUID(),
    state: 'UPCOMING',
    competitionId: crypto.randomUUID(),
    competitionName: 'CM 1x',
    executionOrder: 0,
    currentlyRunning: false,
    teams: [],
    ...overrides,
})

const pendingSlot = (overrides: Partial<PendingSlotDto>): PendingSlotDto => ({
    slotId: crypto.randomUUID(),
    startTime: '2026-08-17T08:00:00',
    ...overrides,
})

describe('buildLiveDashboardTimeline', () => {
    it('sortiert Läufe und wartende Slots gemeinsam nach Startzeit', () => {
        const early = match({matchId: 'early', startTime: '2026-08-17T08:00:00'})
        const late = match({matchId: 'late', startTime: '2026-08-17T09:00:00'})
        const between = pendingSlot({slotId: 'between', startTime: '2026-08-17T08:30:00'})

        const timeline = buildLiveDashboardTimeline([late, early], [between])

        expect(timeline.map(entry => (entry.kind === 'match' ? entry.match.matchId : entry.slot.slotId))).toEqual([
            'early',
            'between',
            'late',
        ])
    })

    it('reiht Läufe ohne Startzeit ans Ende ein', () => {
        const scheduled = match({matchId: 'scheduled', startTime: '2026-08-17T08:00:00'})
        const unscheduled = match({matchId: 'unscheduled', startTime: undefined})

        const timeline = buildLiveDashboardTimeline([unscheduled, scheduled], [])

        expect(timeline.map(entry => (entry.kind === 'match' ? entry.match.matchId : entry.slot.slotId))).toEqual([
            'scheduled',
            'unscheduled',
        ])
    })
})

describe('pendingSlotLabel', () => {
    it('setzt Wettkampf, Runde und Lauf mit Trennzeichen zusammen', () => {
        expect(
            pendingSlotLabel(
                pendingSlot({competitionName: 'CM 1x', roundName: 'Achtelfinale', matchName: 'AF1'}),
            ),
        ).toBe('CM 1x · Achtelfinale · AF1')
    })

    it('lässt fehlende Teile weg', () => {
        expect(pendingSlotLabel(pendingSlot({competitionName: 'CM 1x'}))).toBe('CM 1x')
    })
})
