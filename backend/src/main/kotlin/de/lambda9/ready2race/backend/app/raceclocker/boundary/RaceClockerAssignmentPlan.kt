package de.lambda9.ready2race.backend.app.raceclocker.boundary

import java.util.UUID

/**
 * Rechnet aus, welche Wettkämpfe ihre RaceClocker-Anwahl ändern, wenn EIN Rennen neu zugeordnet
 * wird — die reine Entscheidung, ohne Datenbank, damit die „verschieben"-Regel prüfbar bleibt.
 *
 * Betrachtet wird immer nur EINE Rundenart (Qualifikation ODER Läufe) auf einmal. Regel je
 * Wettkampf:
 * - steht er in [selected] → zeigt danach auf [raceId] (verschiebt sich, falls er vorher woanders
 *   hing — der letzte Klick gewinnt, Entscheidung von Thomas am 11.08.2026);
 * - steht er NICHT in [selected], zeigte aber bisher auf [raceId] → fällt zurück auf „erbt" (null);
 * - zeigt er auf ein ANDERES Rennen oder erbt schon → unverändert (dieses Rennen geht ihn nichts an).
 *
 * Zurück kommen nur die tatsächlichen Änderungen (`competitionId` → neuer Wert, `null` = erbt),
 * damit der Aufruf nur schreibt, was sich wirklich ändert.
 */
object RaceClockerAssignmentPlan {

    fun changes(
        raceId: UUID,
        selected: Set<UUID>,
        current: Map<UUID, UUID?>,
    ): Map<UUID, UUID?> {
        val result = mutableMapOf<UUID, UUID?>()
        for ((competitionId, currentRace) in current) {
            val target = when {
                competitionId in selected -> raceId
                currentRace == raceId -> null
                else -> currentRace
            }
            if (target != currentRace) {
                result[competitionId] = target
            }
        }
        return result
    }
}
