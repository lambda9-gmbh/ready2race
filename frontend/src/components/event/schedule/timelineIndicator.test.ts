import {describe, expect, it} from 'vitest'
import {EventScheduleSlotDto, LiveDashboardMatchDto, PendingSlotDto} from '@api/types.gen.ts'
import {
    computeHourMarks,
    computeNowMarkerPercent,
    computeTimelinePositions,
    dashboardEntriesForDay,
    dashboardMatchState,
    dayOf,
    resolveDashboardDay,
    scheduleSlotsToEntries,
    labelFitsWidth,
    scheduleSlotState,
    timelineEntryAppearance,
    timelineSpan,
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
    matchActivatedAt: null,
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
    it('maps an activated but not yet started slot to preparing', () => {
        // Aktiviert heißt "an den Start gerufen", nicht "unterwegs" - der Balken darf dafür nicht
        // dieselbe Farbe tragen wie ein fahrender Lauf.
        expect(
            scheduleSlotState(
                slot('2026-08-17T08:00:00', {
                    state: 'LINKED',
                    matchActivatedAt: '2026-08-17T07:55:00',
                    matchStartedAt: null,
                }),
            ),
        ).toBe('preparing')
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

    it('gives a match at the start its own look, not the one of a racing match', () => {
        expect(dashboardMatchState(match({state: 'PREPARING'}))).toBe('preparing')
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

describe('entry labelling', () => {
    it('gives schedule entries a short label from the competition tag and the round for the tooltip', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {
                competitionIdentifier: '17',
                competitionShortName: 'CM 4x+',
                matchStartedAt: '2026-08-17T08:03:00',
            }),
        ])
        expect(entries[0].shortLabel).toBe('17 CM 4x+')
        expect(entries[0].roundLabel).toBe('Achtelfinale – AF1')
        expect(entries[0].actualStartTime).toBe('2026-08-17T08:03:00')
    })

    it('falls back to the program item name and then the match name', () => {
        const program = scheduleSlotsToEntries([
            slot('2026-08-17T12:00:00', {name: 'Mittagspause', state: 'FREE'}),
        ])
        expect(program[0].shortLabel).toBe('Mittagspause')
        const noTag = scheduleSlotsToEntries([slot('2026-08-17T08:00:00')])
        expect(noTag[0].shortLabel).toBe('AF1')
    })

    it('labels dashboard entries the same way', () => {
        const entries = dashboardEntriesForDay(
            [
                match({
                    competitionIdentifier: '3',
                    competitionShortName: 'JM 2x',
                    startedAt: '2026-08-17T09:02:00',
                }),
            ],
            [pendingSlot({})],
            '2026-08-17',
        )
        expect(entries[0].shortLabel).toBe('3 JM 2x')
        expect(entries[0].actualStartTime).toBe('2026-08-17T09:02:00')
        expect(entries[1].roundLabel).toBe('Achtelfinale – AF2')
    })
})

describe('labelFitsWidth', () => {
    it('shows a label only when the block is wide enough, and never an empty one', () => {
        expect(labelFitsWidth('17 CM 4x+', 100)).toBe(true)
        expect(labelFitsWidth('17 CM 4x+', 40)).toBe(false)
        expect(labelFitsWidth('', 500)).toBe(false)
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

describe('timelineEntryAppearance', () => {
    it('carries the same color semantics as the status chips', () => {
        // matchStatusChip: beendet = success, läuft = primary, in Vorbereitung = info,
        // wartet auf Beenden = warning — der Balken darf nichts anderes behaupten.
        expect(timelineEntryAppearance('finished')).toMatchObject({
            color: 'success',
            variant: 'filled',
            muted: true,
        })
        expect(timelineEntryAppearance('running')).toMatchObject({color: 'primary', muted: false})
        expect(timelineEntryAppearance('preparing')).toMatchObject({color: 'info'})
        expect(timelineEntryAppearance('awaitingFinish')).toMatchObject({color: 'warning'})
    })

    it('draws pending entries as outlines, dashed while the race is not set yet', () => {
        expect(timelineEntryAppearance('linked')).toMatchObject({
            variant: 'outlined',
            dashed: false,
        })
        expect(timelineEntryAppearance('waiting')).toMatchObject({
            variant: 'outlined',
            dashed: true,
        })
    })

    it('strikes cancelled entries through and keeps them muted, like the cancelled chip', () => {
        expect(timelineEntryAppearance('skipped')).toMatchObject({
            strikeThrough: true,
            muted: true,
            color: 'default',
        })
    })

    it('hatches program items - nobody races there', () => {
        expect(timelineEntryAppearance('free')).toMatchObject({hatched: true, color: 'default'})
    })

    it('hatches byes and mirrors the bye chip colors: open = info, acknowledged = success, cancelled = struck', () => {
        expect(timelineEntryAppearance('linked', true)).toMatchObject({
            hatched: true,
            color: 'info',
        })
        expect(timelineEntryAppearance('finished', true)).toMatchObject({
            hatched: true,
            color: 'success',
            muted: true,
        })
        expect(timelineEntryAppearance('skipped', true)).toMatchObject({
            hatched: true,
            strikeThrough: true,
        })
    })

    it('lets an activated or running bye look like any other race - what happens beats the bye', () => {
        expect(timelineEntryAppearance('running', true)).toMatchObject({
            hatched: false,
            color: 'primary',
        })
        expect(timelineEntryAppearance('preparing', true)).toMatchObject({
            hatched: false,
            color: 'info',
        })
    })
})

describe('bye flag on entries', () => {
    it('marks non-racing byes from schedule slots and dashboard matches, but not must-race byes', () => {
        const bye = {cause: 'DEREGISTERED', mustRace: false} as const
        const mustRace = {cause: 'DEREGISTERED', mustRace: true} as const
        expect(scheduleSlotsToEntries([slot('2026-08-17T08:00:00', {bye})])[0].bye).toBe(true)
        expect(scheduleSlotsToEntries([slot('2026-08-17T08:00:00', {bye: mustRace})])[0].bye).toBe(
            false,
        )
        const entries = dashboardEntriesForDay(
            [match({bye}), match({bye: mustRace})],
            [],
            '2026-08-17',
        )
        expect(entries.map(e => e.bye)).toEqual([true, false])
    })
})

describe('timelineSpan', () => {
    it('rounds the axis outwards to full hours so hour marks close it on both ends', () => {
        const span = timelineSpan(
            scheduleSlotsToEntries([
                slot('2026-08-17T08:15:00', {durationMinutes: 10}),
                slot('2026-08-17T09:40:00', {durationMinutes: 10}),
            ]),
        )
        expect(span).not.toBeNull()
        expect(new Date(span!.startMs).getHours()).toBe(8)
        expect(new Date(span!.startMs).getMinutes()).toBe(0)
        // Letztes Ende 09:50 -> Achse endet 10:00
        expect(new Date(span!.endMs).getHours()).toBe(10)
        expect(new Date(span!.endMs).getMinutes()).toBe(0)
    })

    it('spans at least one hour for a single zero-duration entry', () => {
        const span = timelineSpan(scheduleSlotsToEntries([slot('2026-08-17T12:00:00')]))
        expect(span!.endMs - span!.startMs).toBe(3_600_000)
    })

    it('is null without entries', () => {
        expect(timelineSpan([])).toBeNull()
    })
})

describe('computeHourMarks', () => {
    it('marks every full hour of a short span, first at 0 % and last at 100 %', () => {
        const marks = computeHourMarks(
            scheduleSlotsToEntries([
                slot('2026-08-17T08:00:00'),
                slot('2026-08-17T10:00:00'),
            ]),
        )
        expect(marks.map(m => m.percent)).toEqual([0, 50, 100])
        expect(new Date(marks[1].timeMs).getHours()).toBe(9)
    })

    it('widens the step on long days so at most about ten marks remain', () => {
        const marks = computeHourMarks(
            scheduleSlotsToEntries([
                slot('2026-08-17T06:00:00'),
                // 06:00 bis 22:00 = 16 Stunden -> Schrittweite 2 h statt 17 Marken
                slot('2026-08-17T22:00:00'),
            ]),
        )
        expect(marks.length).toBeLessThanOrEqual(11)
        const hourStep = (marks[1].timeMs - marks[0].timeMs) / 3_600_000
        expect(hourStep).toBe(2)
    })

    it('returns no marks without entries', () => {
        expect(computeHourMarks([])).toEqual([])
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

    it('moves an entry overlapping a still-running one to a second lane', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {durationMinutes: 60}),
            slot('2026-08-17T08:30:00', {durationMinutes: 60}),
            // 09:30 beginnt, wenn beide vorbei sind - zurück in Spur 0
            slot('2026-08-17T09:30:00', {durationMinutes: 30}),
        ])
        const positioned = computeTimelinePositions(entries)
        expect(positioned[0].stackRow).toBe(0)
        expect(positioned[1].stackRow).toBe(1)
        expect(positioned[2].stackRow).toBe(0)
    })

    it('also dodges purely visual overlap caused by the minimum width', () => {
        // Zwei dauerlose Läufe eine Minute auseinander auf einer Stunde Achse: zeitlich
        // überschneidungsfrei, gezeichnet (Mindestbreite!) übereinander -> zweite Spur.
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00'),
            slot('2026-08-17T08:01:00'),
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

    it('returns null when the entries belong to a different calendar day', () => {
        // Wer den Zeitplan von übermorgen ansieht, bekommt kein an den Rand geklemmtes "Jetzt".
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00'),
            slot('2026-08-17T10:00:00'),
        ])
        expect(computeNowMarkerPercent(entries, new Date('2026-08-19T09:00:00'))).toBeNull()
    })
})
