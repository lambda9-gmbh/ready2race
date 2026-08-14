import {describe, expect, it} from 'vitest'
import {formatElapsed, streamClockState} from './streamClock.ts'
import {AthleteBoardMatch, AthleteBoardTeam} from '@api/types.gen.ts'

const team = (overrides: Partial<AthleteBoardTeam>): AthleteBoardTeam =>
    ({startNumber: 1, timeString: null, failed: false, ...overrides}) as AthleteBoardTeam

const matchWith = (overrides: Partial<AthleteBoardMatch>): AthleteBoardMatch =>
    ({matchName: 'VF1', teams: [], actualStartTime: null, ...overrides}) as AthleteBoardMatch

describe('streamClockState', () => {
    it('ist hidden ohne Lauf', () => {
        expect(streamClockState(null, 0, 0)).toEqual({phase: 'hidden'})
    })

    it('ist hidden ohne actualStartTime', () => {
        const match = matchWith({actualStartTime: null, teams: [team({})]})
        expect(streamClockState(match, Date.now(), 0)).toEqual({phase: 'hidden'})
    })

    it('läuft mit korrektem elapsedMs inklusive Versatz-Korrektur', () => {
        const start = new Date('2026-08-13T10:00:00.000Z')
        const match = matchWith({
            actualStartTime: start.toISOString(),
            teams: [team({timeString: null})],
        })
        // Client-Uhr 5 s nach Start; Server 2 s hinter der Client-Uhr (clockOffsetMs =
        // clientNow - serverTime = 2000) → tatsächlich sind nur 3 s Server-Zeit vergangen.
        const clientNowMs = start.getTime() + 5000
        const clockOffsetMs = 2000
        const state = streamClockState(match, clientNowMs, clockOffsetMs)
        expect(state).toEqual({phase: 'running', elapsedMs: 3000})
    })

    it('friert ein, wenn alle Boote gewertet oder failed sind', () => {
        const start = new Date('2026-08-13T10:00:00.000Z')
        const match = matchWith({
            actualStartTime: start.toISOString(),
            teams: [team({timeString: '1:23.4'}), team({startNumber: 2, failed: true})],
        })
        const clientNowMs = start.getTime() + 10000
        const state = streamClockState(match, clientNowMs, 0)
        expect(state).toEqual({phase: 'frozen', elapsedMs: 10000})
    })

    it('läuft weiter, solange mindestens ein Boot ohne Zeit und ohne failed ist', () => {
        const start = new Date('2026-08-13T10:00:00.000Z')
        const match = matchWith({
            actualStartTime: start.toISOString(),
            teams: [team({timeString: '1:23.4'}), team({startNumber: 2, timeString: null})],
        })
        const clientNowMs = start.getTime() + 4000
        const state = streamClockState(match, clientNowMs, 0)
        expect(state).toEqual({phase: 'running', elapsedMs: 4000})
    })

    it('bleibt running, wenn keine Boote existieren', () => {
        const start = new Date('2026-08-13T10:00:00.000Z')
        const match = matchWith({actualStartTime: start.toISOString(), teams: []})
        const clientNowMs = start.getTime() + 1000
        expect(streamClockState(match, clientNowMs, 0)).toEqual({
            phase: 'running',
            elapsedMs: 1000,
        })
    })
})

describe('formatElapsed', () => {
    it("formatiert 83_450 ms als '1:23.4'", () => {
        expect(formatElapsed(83_450)).toBe('1:23.4')
    })

    it("formatiert ab einer Stunde als 'h:mm:ss.z'", () => {
        expect(formatElapsed(3_723_400)).toBe('1:02:03.4')
    })

    it('klemmt negative Werte auf 0', () => {
        expect(formatElapsed(-5000)).toBe('0:00.0')
    })
})
