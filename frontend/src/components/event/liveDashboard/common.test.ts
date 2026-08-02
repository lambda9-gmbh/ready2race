import {describe, expect, it} from 'vitest'
import {LiveDashboardTeamDto} from '@api/types.gen.ts'
import {openResultTeams, teamHasResult} from './common.ts'

const team = (overrides: Partial<LiveDashboardTeamDto>): LiveDashboardTeamDto => ({
    teamId: crypto.randomUUID(),
    failed: false,
    deregistered: false,
    invoiceState: 'NONE',
    participants: [],
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
