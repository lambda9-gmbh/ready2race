import {EventScheduleSlotDto} from '@api/types.gen.ts'

export type DaySection = {date: string; slots: EventScheduleSlotDto[]}

export const groupSlotsByDay = (slots: EventScheduleSlotDto[]): DaySection[] => {
    const sorted = [...slots].sort((a, b) => a.startTime.localeCompare(b.startTime))
    const byDay = new Map<string, EventScheduleSlotDto[]>()
    for (const s of sorted) {
        const day = s.startTime.slice(0, 10)
        byDay.set(day, [...(byDay.get(day) ?? []), s])
    }
    return [...byDay.entries()].map(([date, daySlots]) => ({date, slots: daySlots}))
}

export const slotLabel = (slot: EventScheduleSlotDto): string =>
    slot.name ?? [slot.competitionName, slot.roundName, slot.matchName].filter(Boolean).join(' – ')
