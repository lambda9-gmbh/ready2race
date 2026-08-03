import {describe, expect, it} from 'vitest'
import {groupSlotsByDay, isEditable, slotLabel} from './common.ts'
import {EventScheduleSlotDto} from '@api/types.gen.ts'

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
