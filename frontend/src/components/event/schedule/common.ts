import {EventScheduleSlotDto, ShiftPreviewEntryDto} from '@api/types.gen.ts'

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

// Vorbelegung für den Shift-Dialog: der erste Slot des Tages, der noch nicht gelaufen ist - ein
// bereits beendeter Lauf zu verschieben ergibt fachlich keinen Sinn. Sind alle Slots schon
// beendet (Tag komplett abgeschlossen), bleibt trotzdem der erste Slot als Fallback, statt gar
// keine Vorbelegung anzubieten.
export const defaultFromSlotId = (slots: EventScheduleSlotDto[]): string | undefined =>
    (slots.find(s => !s.matchFinishedAt) ?? slots[0])?.id

// Ziel-Slot-Auswahl für den Modus "Aufholen bis": nur Slots, die im (bereits zeitlich sortierten)
// Tages-Array NACH dem gewählten Start-Slot liegen - das spiegelt die Backend-Regel wider, dass
// der Ziel-Slot hinter dem Start-Slot liegen muss (siehe EventScheduleService.shiftSchedule).
export const slotsAfter = (slots: EventScheduleSlotDto[], fromSlotId: string): EventScheduleSlotDto[] => {
    const idx = slots.findIndex(s => s.id === fromSlotId)
    return idx === -1 ? [] : slots.slice(idx + 1)
}

export type ShiftPreviewRow = {
    slotId: string
    label: string
    oldStartTime: string
    newStartTime: string
    changed: boolean
}

// Reine Anzeige-Aufbereitung der Vorschau-Antwort: löst den Slot-Namen auf und markiert Zeilen,
// deren Zeit sich durch den Shift tatsächlich ändert (damit die Tabelle das hervorheben kann).
export const buildShiftPreviewRows = (
    entries: ShiftPreviewEntryDto[],
    slots: EventScheduleSlotDto[],
): ShiftPreviewRow[] => {
    const bySlotId = new Map(slots.map(s => [s.id, s]))
    return entries.map(entry => {
        const slot = bySlotId.get(entry.slotId)
        return {
            slotId: entry.slotId,
            label: slot ? slotLabel(slot) : entry.slotId,
            oldStartTime: entry.oldStartTime,
            newStartTime: entry.newStartTime,
            changed: entry.oldStartTime !== entry.newStartTime,
        }
    })
}

// Der Server liefert bei CompressionImpossible (422) nur einen Freitext ("Cannot compress: only
// $maxReductionMinutes minutes available", siehe EventScheduleError.kt) statt eines strukturierten
// Felds - die Minutenzahl muss also aus der Nachricht herausgeparst werden, um sie in den i18n-Text
// {{max}} einzusetzen. Passt das Muster nicht (anderer Fehlertext), gibt es undefined zurück, und
// der Dialog zeigt den generischen Invalid-Text.
export const parseMaxReductionMinutes = (message: string): number | undefined => {
    const match = message.match(/only (\d+) minutes available/)
    return match ? Number(match[1]) : undefined
}
