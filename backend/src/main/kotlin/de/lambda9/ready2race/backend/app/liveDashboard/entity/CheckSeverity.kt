package de.lambda9.ready2race.backend.app.liveDashboard.entity

import de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic
import java.util.UUID

/**
 * Die Prüfungen, die das Schiedsrichter-Dashboard bewertet.
 *
 * [REQUIREMENT] und [REQUIREMENT_TIME_WINDOW] beziehen sich auf dieselbe Teilnahmebedingung und
 * sind trotzdem getrennt einstellbar: "gar nicht abgehakt" und "abgehakt, aber zur falschen Zeit"
 * sind am Zelt zwei verschiedene Vorgänge.
 */
enum class CheckType { INVOICE_OPEN, NOT_IN_ARENA, REQUIREMENT, REQUIREMENT_TIME_WINDOW }

/** Was pro Wettkampf eingestellt werden kann. */
enum class CheckSeverity { OK, WARNING, CRITICAL }

/**
 * Was ausgeliefert wird. Die Reihenfolge der Konstanten IST die Rangfolge: [worstSeverity]
 * verlässt sich auf die natürliche Ordnung der Aufzählung.
 *
 * [NEUTRAL] heißt "hierzu gibt es nichts zu sagen" - entweder gilt die Prüfung nicht, oder sie ist
 * nicht erfüllt und ausdrücklich als [CheckSeverity.OK] eingestuft. Genau das ist der graue Kreis:
 * "unbezahlt, wird heute nicht geahndet" darf nicht aussehen wie "bezahlt".
 */
enum class EffectiveSeverity { NEUTRAL, OK, WARNING, CRITICAL }

data class CheckSeverityKey(
    val competitionId: UUID,
    val checkType: CheckType,
    /** Nur bei [CheckType.REQUIREMENT] und [CheckType.REQUIREMENT_TIME_WINDOW] gesetzt. */
    val requirementId: UUID? = null,
)

/**
 * Die abweichend eingestellten Schweregrade einer Veranstaltung. Bewusst nur die Abweichungen:
 * fehlt ein Eintrag, gilt [LiveDashboardLogic.defaultSeverity], und der entspricht dem Verhalten
 * vor dieser Einstellmöglichkeit. Ein neuer Wettkampf und eine neue Teilnahmebedingung sind damit
 * ohne einen einzigen Pflegeschritt richtig eingestellt.
 */
data class CheckSeverityConfig(val overrides: Map<CheckSeverityKey, CheckSeverity>) {

    companion object {
        val empty = CheckSeverityConfig(emptyMap())
    }

    /**
     * Der eingestellte Schweregrad, sonst der Standard. [optional] wirkt nur auf
     * [CheckType.REQUIREMENT] und stammt aus `participant_requirement.optional`.
     */
    fun severityFor(
        competitionId: UUID,
        checkType: CheckType,
        requirementId: UUID? = null,
        optional: Boolean = false,
    ): CheckSeverity =
        overrides[CheckSeverityKey(competitionId, checkType, requirementId)]
            ?: LiveDashboardLogic.defaultSeverity(checkType, optional)
}
