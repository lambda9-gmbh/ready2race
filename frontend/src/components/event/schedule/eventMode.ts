import {EventScheduleSlotDto} from '@api/types.gen.ts'

/**
 * Der Veranstaltungs-Modus des Zeitplan-Tabs: Der Zeitplan wird zum Leitstand in nahezu voller
 * Browserbreite, und der Klick auf eine Lauf-Zeile lädt die Durchführung des Wettkampfs in eine
 * Spalte NEBEN dem Zeitplan, statt die Seite zu wechseln. Hier liegt die reine Auswahl-Logik —
 * ohne React, damit sie testbar bleibt (siehe eventMode.test.ts).
 */

/** Der rechts geladene Wettkampf; null heißt „Fläche leer, wähle einen Lauf". */
export type EventModeSelection = {
    competitionId: string
    /** Für die Kopfzeile der rechten Spalte — aus dem geklickten Slot übernommen. */
    competitionName: string
    /**
     * Der angeklickte Lauf: Die rechte Spalte springt nach dem Laden zu seiner Karte. null bei
     * Slots, deren Lauf noch nicht materialisiert ist — dann lädt nur der Wettkampf, ohne Sprung.
     */
    matchId: string | null
} | null

/**
 * Was der Klick auf eine Slot-Zeile bewirkt: ein anderer Wettkampf wechselt die rechte Spalte,
 * ein anderer Lauf desselben Wettkampfs springt nur dorthin, und der Klick auf den bereits
 * geladenen Lauf (oder eine lauflose Zeile desselben Wettkampfs) schließt die Fläche wieder
 * (Toggle). Slots ohne Wettkampf (freie Programmpunkte) ändern nichts — ihre Zeilen reagieren
 * gar nicht erst, die Regel steht hier trotzdem, damit die Funktion für jeden Slot eine Antwort
 * hat.
 */
export const nextEventModeSelection = (
    current: EventModeSelection,
    slot: Pick<EventScheduleSlotDto, 'competitionId' | 'competitionName' | 'matchId'>,
): EventModeSelection => {
    if (!slot.competitionId) {
        return current
    }
    if (current !== null && current.competitionId === slot.competitionId) {
        if (slot.matchId != null && slot.matchId !== current.matchId) {
            // Gleicher Wettkampf, anderer Lauf: rechts bleibt alles stehen, nur der Sprung
            // wandert — die Spalte lädt nicht neu.
            return {...current, matchId: slot.matchId}
        }
        return null
    }
    return {
        competitionId: slot.competitionId,
        competitionName: slot.competitionName ?? '',
        matchId: slot.matchId ?? null,
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
