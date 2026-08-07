import {describe, expect, it} from 'vitest'
import {EventScheduleSlotDto, LiveDashboardMatchDto, PendingSlotDto} from '@api/types.gen.ts'
import {
    computeNowMarkerPercent,
    computeTimelinePositions,
    dashboardEntriesForDay,
    dashboardMatchState,
    dayOf,
    resolveDashboardDay,
    scheduleSlotsToEntries,
    scheduleSlotState,
} from './timelineIndicator.ts'

const slot = (startTime: string, over: Partial<EventScheduleSlotDto> = {}): EventScheduleSlotDto => ({
    id: crypto.randomUUID(),
    startTime,
    state: 'WAITING',
    name: null,
    durationMinutes: null,
    competitionId: null,
    competitionName: 'CM 1x',
    roundName: 'Achtelfinale',
    matchName: 'AF1',
    matchId: null,
    setupMatchId: crypto.randomUUID(),
    matchStartedAt: null,
    matchFinishedAt: null,
    matchCurrentlyRunning: false,
    matchTeamsTotal: 0,
    matchTeamsScored: 0,
    ...over,
})

const match = (over: Partial<LiveDashboardMatchDto> = {}): LiveDashboardMatchDto => ({
    matchId: crypto.randomUUID(),
    state: 'UPCOMING',
    competitionId: crypto.randomUUID(),
    competitionName: 'CM 1x',
    roundName: 'Achtelfinale',
    matchName: 'AF1',
    executionOrder: 1,
    startTime: '2026-08-17T09:00:00',
    startedAt: null,
    currentlyRunning: false,
    elapsedMinutes: null,
    teams: [],
    ...over,
})

const pendingSlot = (over: Partial<PendingSlotDto> = {}): PendingSlotDto => ({
    slotId: crypto.randomUUID(),
    startTime: '2026-08-17T09:30:00',
    name: null,
    competitionName: 'CM 1x',
    roundName: 'Achtelfinale',
    matchName: 'AF2',
    ...over,
})

describe('scheduleSlotState', () => {
    it('maps a finished slot regardless of its raw state', () => {
        expect(
            scheduleSlotState(slot('2026-08-17T08:00:00', {state: 'LINKED', matchFinishedAt: '2026-08-17T08:20:00'})),
        ).toBe('finished')
    })
    it('maps a started-but-not-finished slot to running', () => {
        expect(
            scheduleSlotState(slot('2026-08-17T08:00:00', {state: 'LINKED', matchStartedAt: '2026-08-17T08:01:00'})),
        ).toBe('running')
    })
    it('maps WAITING/LINKED/FREE/SKIPPED/OBSOLETE to their generic states', () => {
        expect(scheduleSlotState(slot('t', {state: 'WAITING'}))).toBe('waiting')
        expect(scheduleSlotState(slot('t', {state: 'LINKED'}))).toBe('linked')
        expect(scheduleSlotState(slot('t', {state: 'FREE'}))).toBe('free')
        expect(scheduleSlotState(slot('t', {state: 'SKIPPED'}))).toBe('skipped')
        expect(scheduleSlotState(slot('t', {state: 'OBSOLETE'}))).toBe('skipped')
    })
})

describe('dashboardMatchState', () => {
    it('shows a cancelled match struck through like a cancelled slot, instead of dropping it', () => {
        expect(dashboardMatchState(match({state: 'SKIPPED'}))).toBe('skipped')
    })

    it('keeps running and finished ahead of the cancellation', () => {
        expect(dashboardMatchState(match({state: 'RUNNING'}))).toBe('running')
        expect(dashboardMatchState(match({state: 'FINISHED'}))).toBe('finished')
        expect(dashboardMatchState(match({state: 'UPCOMING'}))).toBe('linked')
    })

    it('gives a match waiting to be finished its own look, neither running nor finished', () => {
        expect(dashboardMatchState(match({state: 'AWAITING_FINISH'}))).toBe('awaitingFinish')
    })
})

describe('scheduleSlotsToEntries', () => {
    it('maps slots to generic timeline entries preserving order', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {durationMinutes: 10}),
            slot('2026-08-17T09:00:00', {name: 'Pause', state: 'FREE', competitionName: null, roundName: null, matchName: null}),
        ])
        expect(entries).toHaveLength(2)
        expect(entries[0].label).toBe('CM 1x – Achtelfinale – AF1')
        expect(entries[0].durationMinutes).toBe(10)
        expect(entries[1].label).toBe('Pause')
        expect(entries[1].state).toBe('free')
    })
})

describe('dayOf', () => {
    it('extracts the calendar day from an ISO-like local timestamp', () => {
        expect(dayOf('2026-08-17T08:00:00')).toBe('2026-08-17')
    })
})

describe('dashboardEntriesForDay', () => {
    it('combines matches and pending slots for the given day, dropping other days and missing start times', () => {
        const entries = dashboardEntriesForDay(
            [
                match({startTime: '2026-08-17T09:00:00', state: 'RUNNING'}),
                match({startTime: '2026-08-18T09:00:00', state: 'UPCOMING'}),
                match({startTime: null, state: 'UNSCHEDULED'}),
            ],
            [pendingSlot({startTime: '2026-08-17T09:30:00', name: null})],
            '2026-08-17',
        )
        expect(entries.map(e => e.startTime)).toEqual(['2026-08-17T09:00:00', '2026-08-17T09:30:00'])
        expect(entries[0].state).toBe('running')
        expect(entries[1].state).toBe('waiting')
    })

    it('maps a named pending slot (program item) to the free state', () => {
        const entries = dashboardEntriesForDay(
            [],
            [pendingSlot({startTime: '2026-08-17T12:00:00', name: 'Mittagspause'})],
            '2026-08-17',
        )
        expect(entries[0].state).toBe('free')
        expect(entries[0].label).toBe('Mittagspause')
    })
})

describe('resolveDashboardDay', () => {
    it('picks the day of the first running match', () => {
        const day = resolveDashboardDay(
            [
                match({startTime: '2026-08-18T09:00:00', state: 'RUNNING'}),
                match({startTime: '2026-08-17T09:00:00', state: 'FINISHED'}),
            ],
            [],
            new Date('2026-08-20T00:00:00'),
        )
        expect(day).toBe('2026-08-18')
    })

    it('also stays on a day whose match is only waiting to be finished', () => {
        const day = resolveDashboardDay(
            [
                match({startTime: '2026-08-18T09:00:00', state: 'AWAITING_FINISH'}),
                match({startTime: '2026-08-19T09:00:00', state: 'UPCOMING'}),
            ],
            [],
            new Date('2026-08-20T00:00:00'),
        )
        expect(day).toBe('2026-08-18')
    })

    it('falls back to the next upcoming entry when nothing is running', () => {
        const day = resolveDashboardDay(
            [match({startTime: '2026-08-19T09:00:00', state: 'UPCOMING'})],
            [pendingSlot({startTime: '2026-08-19T08:00:00'})],
            new Date('2026-08-20T00:00:00'),
        )
        expect(day).toBe('2026-08-19')
    })

    it('falls back to today when there is nothing running or upcoming', () => {
        const day = resolveDashboardDay([], [], new Date('2026-08-20T00:00:00'))
        expect(day).toBe('2026-08-20')
    })
})

describe('computeTimelinePositions', () => {
    it('positions entries proportionally across the day span', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00'),
            slot('2026-08-17T09:00:00'),
            slot('2026-08-17T10:00:00'),
        ])
        const positioned = computeTimelinePositions(entries)
        expect(positioned[0].leftPercent).toBeCloseTo(0)
        expect(positioned[1].leftPercent).toBeCloseTo(50)
        expect(positioned[2].leftPercent).toBeCloseTo(100)
    })

    it('enforces a minimum segment width for very short or zero-duration slots', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {durationMinutes: 0}),
            slot('2026-08-17T20:00:00', {durationMinutes: 0}),
        ])
        const positioned = computeTimelinePositions(entries)
        expect(positioned[0].widthPercent).toBeGreaterThan(0)
    })

    it('stacks entries that share the exact same start time into separate rows', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {name: 'A', state: 'FREE'}),
            slot('2026-08-17T08:00:00', {name: 'B', state: 'FREE'}),
        ])
        const positioned = computeTimelinePositions(entries)
        expect(positioned[0].stackRow).toBe(0)
        expect(positioned[1].stackRow).toBe(1)
    })

    it('returns an empty array for no entries', () => {
        expect(computeTimelinePositions([])).toEqual([])
    })
})

describe('computeNowMarkerPercent', () => {
    it('places the marker proportionally between the first and last entry', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00'),
            slot('2026-08-17T10:00:00'),
        ])
        const percent = computeNowMarkerPercent(entries, new Date('2026-08-17T09:00:00'))
        expect(percent).toBeCloseTo(50)
    })

    it('clamps to the [0, 100] range when now lies outside the span', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00'),
            slot('2026-08-17T10:00:00'),
        ])
        expect(computeNowMarkerPercent(entries, new Date('2026-08-17T06:00:00'))).toBe(0)
        expect(computeNowMarkerPercent(entries, new Date('2026-08-17T23:00:00'))).toBe(100)
    })

    it('returns null when there are no entries', () => {
        expect(computeNowMarkerPercent([], new Date())).toBeNull()
    })
})
