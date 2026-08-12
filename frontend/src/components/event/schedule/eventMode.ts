import {EventScheduleSlotDto} from '@api/types.gen.ts'

/**
 * Der Veranstaltungs-Modus des Zeitplan-Tabs: Der Zeitplan wird zum Leitstand in nahezu voller
 * Browserbreite, und der Sprung „Zur Durchführung" lädt die Durchführung des Wettkampfs in eine
 * Spalte NEBEN dem Zeitplan, statt die Seite zu wechseln. Hier liegt die reine Auswahl-Logik —
 * ohne React, damit sie testbar bleibt (siehe eventMode.test.ts).
 */

/** Der rechts geladene Wettkampf; null heißt „Fläche leer, wähle einen Lauf". */
export type EventModeSelection = {
    competitionId: string
    /** Für die Kopfzeile der rechten Spalte — aus dem geklickten Slot übernommen. */
    competitionName: string
} | null

/**
 * Was der Klick auf den Durchführungs-Sprung eines Slots bewirkt: ein anderer Wettkampf wechselt
 * die rechte Spalte, derselbe Wettkampf schließt sie wieder (Toggle). Slots ohne Wettkampf (freie
 * Programmpunkte) ändern nichts — ihnen bietet die Tabelle den Sprung gar nicht erst an, die
 * Regel steht hier trotzdem, damit die Funktion für jeden Slot eine Antwort hat.
 */
export const nextEventModeSelection = (
    current: EventModeSelection,
    slot: Pick<EventScheduleSlotDto, 'competitionId' | 'competitionName'>,
): EventModeSelection => {
    if (!slot.competitionId) {
        return current
    }
    if (current !== null && current.competitionId === slot.competitionId) {
        return null
    }
    return {
        competitionId: slot.competitionId,
        competitionName: slot.competitionName ?? '',
    }
}

/**
 * Ob die Zeile dieses Slots als „gerade rechts geladen" hervorgehoben wird. Markiert werden alle
 * Läufe des gewählten Wettkampfs — die Durchführung rechts zeigt ja auch den ganzen Wettkampf,
 * nicht nur den angeklickten Lauf.
 */
export const isSlotSelected = (
    selection: EventModeSelection,
    slot: Pick<EventScheduleSlotDto, 'competitionId'>,
): boolean => selection !== null && slot.competitionId === selection.competitionId
