import {describe, expect, it} from 'vitest'
import {
    buildShiftPreviewRows,
    competitionTag,
    defaultFromSlotId,
    extractMaxReductionMinutes,
    groupSlotsByDay,
    hasBlockingImportRows,
    hasRunningOrFinishedSlots,
    importRowChipColor,
    isCancellable,
    isEditable,
    parseMaxReductionMinutes,
    slotLabel,
    slotsAfter,
    slotsInRound,
} from './common.ts'
import {EventScheduleSlotDto, ImportRowResultDto, ShiftPreviewEntryDto} from '@api/types.gen.ts'

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
    setupRoundId: crypto.randomUUID(),
    matchStartedAt: null,
    matchFinishedAt: null,
    matchCurrentlyRunning: false,
    matchTeamsTotal: 0,
    matchTeamsScored: 0,
    ...over,
})

describe('groupSlotsByDay', () => {
    it('splits by calendar day and keeps time order', () => {
        const sections = groupSlotsByDay([
            slot('2026-08-17T08:00:00'),
            slot('2026-08-16T09:30:00'),
            slot('2026-08-17T10:00:00'),
        ])
        expect(sections.map(s => s.date)).toEqual(['2026-08-16', '2026-08-17'])
        expect(sections[1].slots.map(s => s.startTime)).toEqual([
            '2026-08-17T08:00:00',
            '2026-08-17T10:00:00',
        ])
    })
})

describe('slotsInRound', () => {
    it('returns only the slots sharing the given setup round id', () => {
        const roundA = crypto.randomUUID()
        const roundB = crypto.randomUUID()
        const a1 = slot('2026-08-17T08:00:00', {setupRoundId: roundA})
        const a2 = slot('2026-08-17T08:10:00', {setupRoundId: roundA})
        const b1 = slot('2026-08-17T08:20:00', {setupRoundId: roundB})
        const free = slot('2026-08-17T08:30:00', {setupRoundId: null, setupMatchId: null})

        expect(slotsInRound([a1, a2, b1, free], roundA)).toEqual([a1, a2])
    })

    it('returns an empty list when no slot matches', () => {
        expect(slotsInRound([slot('2026-08-17T08:00:00')], crypto.randomUUID())).toEqual([])
    })
})

describe('slotLabel', () => {
    it('joins competition, round and match name', () => {
        expect(slotLabel(slot('2026-08-17T08:00:00'))).toBe('CM 1x – Achtelfinale – AF1')
    })
    it('uses the free-slot name as-is', () => {
        expect(
            slotLabel(
                slot('2026-08-17T12:00:00', {
                    name: 'Mittagspause',
                    competitionName: null,
                    roundName: null,
                    matchName: null,
                    state: 'FREE',
                    setupMatchId: null,
                }),
            ),
        ).toBe('Mittagspause')
    })
})

describe('competitionTag', () => {
    it('joins race number and short name', () => {
        expect(
            competitionTag({competitionIdentifier: '17', competitionShortName: 'CM 4x+'}),
        ).toBe('17 CM 4x+')
    })
    it('falls back to the race number when no short name is maintained', () => {
        expect(competitionTag({competitionIdentifier: '17', competitionShortName: null})).toBe('17')
    })
    it('is empty for a slot without a competition', () => {
        expect(competitionTag({competitionIdentifier: null, competitionShortName: null})).toBe('')
    })
})

describe('isEditable', () => {
    it('allows editing a free slot', () => {
        expect(
            isEditable(slot('2026-08-17T08:00:00', {state: 'FREE', setupMatchId: null})),
        ).toBe(true)
    })

    it('allows editing a WAITING match slot even without a materialized match', () => {
        expect(
            isEditable(slot('2026-08-17T08:00:00', {state: 'WAITING', matchId: null})),
        ).toBe(true)
    })

    it('allows editing a LINKED match slot', () => {
        const id = crypto.randomUUID()
        expect(
            isEditable(
                slot('2026-08-17T08:00:00', {state: 'LINKED', matchId: id, setupMatchId: id}),
            ),
        ).toBe(true)
    })

    it('does not allow editing an OBSOLETE slot, even though setupMatchId is still populated', () => {
        expect(
            isEditable(
                slot('2026-08-17T08:00:00', {
                    state: 'OBSOLETE',
                    matchId: null,
                    setupMatchId: crypto.randomUUID(),
                }),
            ),
        ).toBe(false)
    })
})

describe('isCancellable', () => {
    it('offers the cancel action for a slot whose match has not begun', () => {
        expect(isCancellable(slot('2026-08-17T08:00:00', {state: 'LINKED'}))).toBe(true)
    })

    it('hides the cancel action for an activated match that has no recorded start yet', () => {
        // Befund B: zwischen "Boote gehen an den Start" und der ersten Meldung der Zeitnahme ist
        // matchStartedAt noch leer - der Server lehnt die Absage trotzdem ab.
        expect(
            isCancellable(
                slot('2026-08-17T08:00:00', {state: 'LINKED', matchCurrentlyRunning: true}),
            ),
        ).toBe(false)
    })

    it('hides the cancel action once the timing has reported a start', () => {
        expect(
            isCancellable(
                slot('2026-08-17T08:00:00', {
                    state: 'LINKED',
                    matchStartedAt: '2026-08-17T08:01:00',
                }),
            ),
        ).toBe(false)
    })
})

describe('defaultFromSlotId', () => {
    it('picks the first slot without matchFinishedAt', () => {
        const finished = slot('2026-08-17T08:00:00', {matchFinishedAt: '2026-08-17T08:20:00'})
        const open = slot('2026-08-17T09:00:00')
        const later = slot('2026-08-17T10:00:00')
        expect(defaultFromSlotId([finished, open, later])).toBe(open.id)
    })

    it('falls back to the first slot when every slot is already finished', () => {
        const first = slot('2026-08-17T08:00:00', {matchFinishedAt: '2026-08-17T08:20:00'})
        const second = slot('2026-08-17T09:00:00', {matchFinishedAt: '2026-08-17T09:20:00'})
        expect(defaultFromSlotId([first, second])).toBe(first.id)
    })

    it('returns undefined for an empty day', () => {
        expect(defaultFromSlotId([])).toBeUndefined()
    })
})

describe('slotsAfter', () => {
    it('returns only the slots later than the chosen from-slot, in order', () => {
        const a = slot('2026-08-17T08:00:00')
        const b = slot('2026-08-17T09:00:00')
        const c = slot('2026-08-17T10:00:00')
        expect(slotsAfter([a, b, c], a.id)).toEqual([b, c])
        expect(slotsAfter([a, b, c], b.id)).toEqual([c])
        expect(slotsAfter([a, b, c], c.id)).toEqual([])
    })

    it('returns an empty list when the from-slot is unknown', () => {
        const a = slot('2026-08-17T08:00:00')
        expect(slotsAfter([a], 'not-a-real-id')).toEqual([])
    })
})

describe('buildShiftPreviewRows', () => {
    it('marks rows whose time actually changed and resolves the slot label', () => {
        const unchanged = slot('2026-08-17T08:00:00', {name: 'Eröffnung', state: 'FREE'})
        const changed = slot('2026-08-17T09:00:00')
        const entries: ShiftPreviewEntryDto[] = [
            {
                slotId: unchanged.id,
                oldStartTime: '2026-08-17T08:00:00',
                newStartTime: '2026-08-17T08:00:00',
            },
            {
                slotId: changed.id,
                oldStartTime: '2026-08-17T09:00:00',
                newStartTime: '2026-08-17T09:15:00',
            },
        ]
        const rows = buildShiftPreviewRows(entries, [unchanged, changed])
        expect(rows).toEqual([
            {
                slotId: unchanged.id,
                label: 'Eröffnung',
                oldStartTime: '2026-08-17T08:00:00',
                newStartTime: '2026-08-17T08:00:00',
                changed: false,
            },
            {
                slotId: changed.id,
                label: slotLabel(changed),
                oldStartTime: '2026-08-17T09:00:00',
                newStartTime: '2026-08-17T09:15:00',
                changed: true,
            },
        ])
    })

    it('falls back to the raw slot id when the slot cannot be resolved', () => {
        const rows = buildShiftPreviewRows(
            [{slotId: 'ghost', oldStartTime: '2026-08-17T08:00:00', newStartTime: '2026-08-17T08:10:00'}],
            [],
        )
        expect(rows[0].label).toBe('ghost')
    })
})

describe('parseMaxReductionMinutes', () => {
    it('extracts the minute count from the backend CompressionImpossible message', () => {
        expect(parseMaxReductionMinutes('Cannot compress: only 7 minutes available')).toBe(7)
    })

    it('returns undefined when the message does not match the expected shape', () => {
        expect(parseMaxReductionMinutes('Shift request parameters are inconsistent')).toBeUndefined()
    })
})

describe('extractMaxReductionMinutes', () => {
    it('prefers the structured details field over the message', () => {
        expect(
            extractMaxReductionMinutes({
                message: 'Cannot compress: only 999 minutes available',
                details: {maxReductionMinutes: 7},
            }),
        ).toBe(7)
    })

    it('falls back to parsing the message when details is missing', () => {
        expect(
            extractMaxReductionMinutes({message: 'Cannot compress: only 7 minutes available'}),
        ).toBe(7)
    })

    it('falls back to parsing the message when details does not carry the field', () => {
        expect(
            extractMaxReductionMinutes({
                message: 'Cannot compress: only 7 minutes available',
                details: {reason: 'INVALID'},
            }),
        ).toBe(7)
    })

    it('returns undefined when neither details nor the message carry a value', () => {
        expect(
            extractMaxReductionMinutes({message: 'Shift request parameters are inconsistent'}),
        ).toBeUndefined()
    })
})

const importRow = (over: Partial<ImportRowResultDto> = {}): ImportRowResultDto => ({
    rowNumber: 1,
    startTime: '2026-08-17T08:00:00',
    competitionText: 'CM 1x',
    laufText: 'Achtelfinale AF1',
    status: 'LINKED',
    targetLabel: 'CM 1x – Achtelfinale – AF1',
    availableMatches: [],
    ...over,
})

describe('hasBlockingImportRows', () => {
    it('is false when every row imports cleanly', () => {
        expect(
            hasBlockingImportRows([importRow({status: 'LINKED'}), importRow({status: 'FREE'})]),
        ).toBe(false)
    })

    it('is false for AMBIGUOUS rows - they just fall back to a free slot', () => {
        expect(hasBlockingImportRows([importRow({status: 'AMBIGUOUS'})])).toBe(false)
    })

    it('is false for rows whose competition or race was not found - also just free slots', () => {
        expect(
            hasBlockingImportRows([
                importRow({status: 'COMPETITION_NOT_FOUND'}),
                importRow({status: 'MATCH_NOT_FOUND', availableMatches: ['HF1']}),
            ]),
        ).toBe(false)
    })

    it('is true as soon as one row is a DUPLICATE', () => {
        expect(
            hasBlockingImportRows([importRow({status: 'LINKED'}), importRow({status: 'DUPLICATE'})]),
        ).toBe(true)
    })

    it('is false for an empty preview', () => {
        expect(hasBlockingImportRows([])).toBe(false)
    })
})

describe('importRowChipColor', () => {
    it('maps each status to its chip color', () => {
        expect(importRowChipColor('LINKED')).toBe('success')
        expect(importRowChipColor('FREE')).toBe('default')
        expect(importRowChipColor('COMPETITION_NOT_FOUND')).toBe('warning')
        expect(importRowChipColor('MATCH_NOT_FOUND')).toBe('warning')
        expect(importRowChipColor('AMBIGUOUS')).toBe('warning')
        expect(importRowChipColor('DUPLICATE')).toBe('error')
    })
})

describe('hasRunningOrFinishedSlots', () => {
    it('is false when no slot has started or finished', () => {
        expect(
            hasRunningOrFinishedSlots([
                slot('2026-08-17T08:00:00'),
                slot('2026-08-17T09:00:00'),
            ]),
        ).toBe(false)
    })

    it('is true when a slot has started', () => {
        expect(
            hasRunningOrFinishedSlots([
                slot('2026-08-17T08:00:00', {matchStartedAt: '2026-08-17T08:01:00'}),
            ]),
        ).toBe(true)
    })

    it('is true when a slot has finished', () => {
        expect(
            hasRunningOrFinishedSlots([
                slot('2026-08-17T08:00:00', {matchFinishedAt: '2026-08-17T08:20:00'}),
            ]),
        ).toBe(true)
    })

    it('is false for an empty schedule', () => {
        expect(hasRunningOrFinishedSlots([])).toBe(false)
    })
})
