import {ChipProps} from '@mui/material'
import {
    EventScheduleSlotDto,
    ImportRowResultDto,
    ImportRowStatus,
    ShiftPreviewEntryDto,
} from '@api/types.gen.ts'

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

// Zählt die Slots derselben Setup-Runde (client-seitig, ohne Zusatzrequest) - für die Bestätigung
// vor "Runde überspringen" (siehe EventScheduleService.setRoundSkipped): wie viele Slots wären
// betroffen, damit der Dialog das nicht nur behauptet, sondern konkret nennt.
export const slotsInRound = (
    slots: EventScheduleSlotDto[],
    setupRoundId: string,
): EventScheduleSlotDto[] => slots.filter(s => s.setupRoundId === setupRoundId)

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

// Fallback für den Fall, dass der Server (noch) keine strukturierten details mitschickt, z. B. bei
// einer älteren Backend-Version oder wenn details aus irgendeinem Grund fehlt - dann wird die
// Minutenzahl aus dem Freitext ("Cannot compress: only $maxReductionMinutes minutes available",
// siehe EventScheduleError.kt) herausgeparst. Passt das Muster nicht, gibt es undefined zurück, und
// der Dialog zeigt den generischen Invalid-Text.
export const parseMaxReductionMinutes = (message: string): number | undefined => {
    const match = message.match(/only (\d+) minutes available/)
    return match ? Number(match[1]) : undefined
}

// Primärer Weg (seit EventScheduleError.CompressionImpossible details mitschickt, siehe
// EventScheduleError.kt): die Minutenzahl kommt maschinenlesbar aus error.details, statt aus der
// (übersetzbaren, änderbaren) Nachricht geparst zu werden. Fehlt details (siehe parseMaxReductionMinutes),
// bleibt der Regex-Fallback als Sicherheitsnetz.
export const extractMaxReductionMinutes = (error: {
    message: string
    details?: unknown
}): number | undefined => {
    const details = error.details as {maxReductionMinutes?: number} | undefined
    if (typeof details?.maxReductionMinutes === 'number') {
        return details.maxReductionMinutes
    }
    return parseMaxReductionMinutes(error.message)
}

// DUPLICATE-Zeilen blockieren den scharfen Import serverseitig (siehe EventScheduleService.
// importSchedule: bei dryRun=false führt ein verbliebenes Duplikat zu 422 DuplicateImportRow) -
// der Import-Button bleibt deshalb schon in der Vorschau gesperrt, statt den Nutzer erst beim
// Anwenden scheitern zu lassen. AMBIGUOUS ist dagegen kein Blocker: diese Zeilen fallen bewusst
// auf einen freien Slot zurück (siehe rowAmbiguous-Hinweistext) und werden trotzdem importiert.
export const hasBlockingImportRows = (rows: ImportRowResultDto[]): boolean =>
    rows.some(row => row.status === 'DUPLICATE')

// Reine Farbzuordnung für den Status-Chip der Vorschau-Tabelle, getrennt von der Übersetzung
// des Labels, damit sie ohne i18n-Kontext testbar ist.
export const importRowChipColor = (status: ImportRowStatus): ChipProps['color'] => {
    switch (status) {
        case 'LINKED':
            return 'success'
        case 'AMBIGUOUS':
            return 'warning'
        case 'DUPLICATE':
            return 'error'
        case 'FREE':
        default:
            return 'default'
    }
}

// Warnung im Import-Dialog: ein Import ersetzt ALLE Slots des Events (siehe replacesAll-Hinweis),
// auch solche mit einem bereits gestarteten oder beendeten Lauf. matchStartedAt/matchFinishedAt
// kommen pro Slot aus competition_match (siehe EventScheduleService.getSchedule) - die Zeitnahme
// ist nur einer der Schreiber. Sind sie für mindestens einen Slot gesetzt, macht der Dialog das
// Risiko sichtbar, bevor der Nutzer den scharfen Import auslöst.
export const hasRunningOrFinishedSlots = (slots: EventScheduleSlotDto[]): boolean =>
    slots.some(s => s.matchStartedAt != null || s.matchFinishedAt != null)
