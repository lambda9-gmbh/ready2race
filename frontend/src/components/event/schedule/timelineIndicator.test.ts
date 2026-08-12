import {describe, expect, it} from 'vitest'
import {EventScheduleSlotDto, LiveDashboardMatchDto, PendingSlotDto} from '@api/types.gen.ts'
import {
    axisLabelAnchor,
    axisLabelPx,
    computeHourMarks,
    computeNowMarkerPercent,
    computeTimelinePositions,
    computeTimelineProjection,
    dashboardEntriesForDay,
    dashboardMatchState,
    dayOf,
    nowLabelHidesHourLabel,
    resolveDashboardDay,
    scheduleSlotsToEntries,
    labelFitsWidth,
    scheduleSlotState,
    timelineEntryAppearance,
    timelineSpan,
} from './timelineIndicator.ts'

const slot = (
    startTime: string,
    over: Partial<EventScheduleSlotDto> = {},
): EventScheduleSlotDto => ({
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
            scheduleSlotState(
                slot('2026-08-17T08:00:00', {
                    state: 'LINKED',
                    matchFinishedAt: '2026-08-17T08:20:00',
                }),
            ),
        ).toBe('finished')
    })
    it('maps a started-but-not-finished slot to running', () => {
        expect(
            scheduleSlotState(
                slot('2026-08-17T08:00:00', {
                    state: 'LINKED',
                    matchStartedAt: '2026-08-17T08:01:00',
                }),
            ),
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
            slot('2026-08-17T09:00:00', {
                name: 'Pause',
                state: 'FREE',
                competitionName: null,
                roundName: null,
                matchName: null,
            }),
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
        expect(entries.map(e => e.startTime)).toEqual([
            '2026-08-17T09:00:00',
            '2026-08-17T09:30:00',
        ])
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
        const bye = {cause: 'DEREGISTRATION', mustRace: false} as const
        const mustRace = {cause: 'DEREGISTRATION', mustRace: true} as const
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
    // Ein üblicher Renntag: Achse 06:00-22:00 = 16 Stunden.
    const raceDay = () =>
        scheduleSlotsToEntries([slot('2026-08-17T06:00:00'), slot('2026-08-17T22:00:00')])

    it('marks every full hour of a short span, first at 0 % and last at 100 %', () => {
        const {marks, format} = computeHourMarks(
            scheduleSlotsToEntries([slot('2026-08-17T08:00:00'), slot('2026-08-17T10:00:00')]),
            1200,
        )
        expect(marks.map(m => m.percent)).toEqual([0, 50, 100])
        expect(new Date(marks[1].timeMs).getHours()).toBe(9)
        expect(format).toBe('full')
    })

    it('labels every hour in full on a wide desktop surface', () => {
        const plan = computeHourMarks(raceDay(), 1400)
        expect(plan.format).toBe('full')
        expect(plan.stepHours).toBe(1)
        // Genug Pixel je Label: 1400 / 16 Marken-Abstände = 87,5 px je "08:00"
        expect(plan.marks).toHaveLength(17)
    })

    it('falls back to bare hours on a phone instead of overlapping full labels', () => {
        // 375 px / 16 h = 23,4 px je Stunde: "08:00" (~44 px) kollidiert, "8" (~13 px) nicht
        // bei Schrittweite 2 -> mobil nackte Stunden statt ineinandergeschobener Uhrzeiten.
        const plan = computeHourMarks(raceDay(), 375)
        expect(plan.format).toBe('hour')
        expect(plan.marks.length).toBeGreaterThan(2)
        // Kollisionfreiheit strukturell: Markenabstand in Pixeln >= Labelbreite + Luft.
        const spacingPx = ((plan.marks[1].percent - plan.marks[0].percent) / 100) * 375
        expect(spacingPx).toBeGreaterThanOrEqual(axisLabelPx('hour') + 12)
    })

    it('keeps full labels at tablet width', () => {
        // 768 px / 16 h = 48 px je Stunde: für "08:00" reicht Schrittweite 2 (96 px je Label).
        const plan = computeHourMarks(raceDay(), 768)
        expect(plan.format).toBe('full')
        const spacingPx = ((plan.marks[1].percent - plan.marks[0].percent) / 100) * 768
        expect(spacingPx).toBeGreaterThanOrEqual(axisLabelPx('full') + 12)
    })

    it('prefers fewer marks over overlapping ones when the surface gets absurdly narrow', () => {
        const plan = computeHourMarks(raceDay(), 120)
        // Egal welches Format: kein Markenpaar darf sich gezeichnet überlappen.
        const spacingPx = ((plan.marks[1].percent - plan.marks[0].percent) / 100) * 120
        expect(spacingPx).toBeGreaterThanOrEqual(axisLabelPx(plan.format) + 12)
    })

    it('plans with a fallback width while the surface is not measured yet', () => {
        // containerPx 0 (erste Renderrunde) darf nicht "nichts passt" bedeuten.
        const plan = computeHourMarks(raceDay(), 0)
        expect(plan.marks.length).toBeGreaterThan(2)
    })

    it('returns no marks without entries', () => {
        expect(computeHourMarks([], 1200).marks).toEqual([])
    })
})

describe('axisLabelAnchor', () => {
    it('anchors edge labels inwards so they cannot clip out of the container', () => {
        // Label 40 px breit auf 400 px: Mitte bei 0 % ragte 20 px nach links hinaus.
        expect(axisLabelAnchor(0, 40, 400)).toBe('start')
        expect(axisLabelAnchor(50, 40, 400)).toBe('center')
        expect(axisLabelAnchor(100, 40, 400)).toBe('end')
    })

    it('also folds labels NEAR the edge, measured in pixels rather than percent', () => {
        // 4 % von 400 px = 16 px < halbe Labelbreite -> zentriert ragte es hinaus.
        expect(axisLabelAnchor(4, 40, 400)).toBe('start')
        expect(axisLabelAnchor(96, 40, 400)).toBe('end')
        // Auf einer breiten Fläche ist dieselbe Prozentposition unkritisch.
        expect(axisLabelAnchor(4, 40, 2000)).toBe('center')
    })
})

describe('nowLabelHidesHourLabel', () => {
    it('hides hour labels whose pixels would collide with the now label', () => {
        // 375 px, Marke 5 % neben dem Jetzt-Marker = 18,75 px Abstand < halbe Labelbreiten.
        expect(nowLabelHidesHourLabel(45, 50, axisLabelPx('hour'), 375)).toBe(true)
        // Auf 1400 px sind 5 % = 70 px - genug Luft, das Label bleibt.
        expect(nowLabelHidesHourLabel(45, 50, axisLabelPx('hour'), 1400)).toBe(false)
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

describe('computeTimelineProjection', () => {
    // Achse 08:00-10:00. Ein um 10 Minuten verspätet gestarteter Lauf plus zwei noch offene.
    const entriesWithDelay = () =>
        scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {
                state: 'LINKED',
                durationMinutes: 20,
                matchStartedAt: '2026-08-17T08:10:00',
            }),
            slot('2026-08-17T09:00:00', {state: 'LINKED', durationMinutes: 20}),
            slot('2026-08-17T09:30:00', {state: 'WAITING', durationMinutes: 20}),
        ])

    it('places the actual-start layer at the real start on the same axis', () => {
        const projection = computeTimelineProjection(
            entriesWithDelay(),
            new Date('2026-08-17T08:30:00'),
        )
        // 08:10 auf der Achse 08:00-10:00 = 8,33 %
        const [actual] = [...projection.actualLeftPercent.values()]
        expect(actual).toBeCloseTo((10 / 120) * 100, 5)
    })

    it('shifts pending entries by the latest start delay, as an expectation with the delay rule of the boards', () => {
        const entries = entriesWithDelay()
        const projection = computeTimelineProjection(entries, new Date('2026-08-17T08:30:00'))
        expect(projection.delaySeconds).toBe(600)
        // Beide offenen Einträge bekommen eine um 10 Minuten verschobene Andeutung.
        expect(projection.expected.size).toBe(2)
        const expected = projection.expected.get(entries[1].id)
        expect(expected).toBeDefined()
        expect(expected!.expectedStartMs).toBe(new Date('2026-08-17T09:10:00').getTime())
        expect(expected!.leftPercent).toBeCloseTo((70 / 120) * 100, 5)
    })

    it('gives no expectation to entries whose expected start is already in the past', () => {
        const entries = entriesWithDelay()
        // 09:15: der 09:00-Eintrag wäre um 09:10 erwartet gewesen - die Andeutung ist widerlegt.
        // Der 09:30-Eintrag (erwartet 09:40) behält seine.
        const projection = computeTimelineProjection(entries, new Date('2026-08-17T09:15:00'))
        expect(projection.expected.has(entries[1].id)).toBe(false)
        expect(projection.expected.has(entries[2].id)).toBe(true)
    })

    it('suggests nothing while the schedule is on time (below one minute), same threshold as delayParts', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {
                state: 'LINKED',
                matchStartedAt: '2026-08-17T08:00:30',
            }),
            slot('2026-08-17T09:00:00', {state: 'LINKED'}),
        ])
        const projection = computeTimelineProjection(entries, new Date('2026-08-17T08:30:00'))
        expect(projection.expected.size).toBe(0)
    })

    it('suggests nothing when no entry has started yet', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {state: 'LINKED'}),
            slot('2026-08-17T09:00:00', {state: 'LINKED'}),
        ])
        const projection = computeTimelineProjection(entries, new Date('2026-08-17T07:00:00'))
        expect(projection.delaySeconds).toBeNull()
        expect(projection.expected.size).toBe(0)
        expect(projection.actualLeftPercent.size).toBe(0)
    })

    it('never projects finished, running or cancelled entries', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {
                state: 'LINKED',
                matchStartedAt: '2026-08-17T08:10:00',
                matchFinishedAt: '2026-08-17T08:20:00',
            }),
            slot('2026-08-17T09:00:00', {state: 'SKIPPED'}),
        ])
        const projection = computeTimelineProjection(entries, new Date('2026-08-17T08:30:00'))
        expect(projection.expected.size).toBe(0)
        // Der beendete Lauf behält seine Ist-Ebene - Soll/Ist bleibt auch rückblickend ablesbar.
        expect(projection.actualLeftPercent.size).toBe(1)
    })

    it('clamps expected positions to the axis', () => {
        const entries = scheduleSlotsToEntries([
            slot('2026-08-17T08:00:00', {
                state: 'LINKED',
                matchStartedAt: '2026-08-17T09:30:00',
            }),
            slot('2026-08-17T08:50:00', {state: 'LINKED'}),
        ])
        // 90 Minuten Verzug schöbe den 08:50-Eintrag auf 10:20 - hinter das Achsenende 09:00...
        const projection = computeTimelineProjection(entries, new Date('2026-08-17T09:31:00'))
        const expected = projection.expected.get(entries[1].id)
        expect(expected).toBeDefined()
        expect(expected!.leftPercent).toBeLessThanOrEqual(100)
    })
})
