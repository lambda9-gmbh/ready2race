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

// Editieren ist für freie Slots und für Match-Slots möglich, solange die Setup-Zeile noch
// existiert (setupMatchId) - das deckt FREE, WAITING und LINKED ab. Nur OBSOLETE (die Setup-Zeile
// wurde gelöscht/das Match existiert nicht mehr) bleibt ein Sackgasse ohne Bearbeiten. setupMatchId
// wird dafür unabhängig von der Materialisierung befüllt (siehe EventScheduleService.getSchedule),
// anders als matchId, das weiterhin nur gesetzt ist, wenn die competition_match-Zeile existiert.
export const isEditable = (slot: EventScheduleSlotDto): boolean =>
    slot.state === 'FREE' || slot.state === 'WAITING' || slot.matchId != null
