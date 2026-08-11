package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

/**
 * Die umgekehrte Sicht auf die RaceClocker-Zuordnung: nicht „welches Rennen nutzt dieser
 * Wettkampf", sondern „welche Wettkämpfe hängen an diesem Rennen". Der Anwender hakt am Rennen die
 * Wettkämpfe an, statt sich durch jeden Wettkampf zu klicken (Wunsch vom 10.08.2026).
 *
 * Geliefert wird die Liste ALLER Wettkämpfe der Veranstaltung mit ihrer aktuellen expliziten
 * Anwahl — leer heißt „erbt die Voreinstellung der Veranstaltung". Die Oberfläche entscheidet
 * daraus, welche Kästchen bei welchem Rennen gesetzt sind, und zeigt geerbte Wettkämpfe gedämpft.
 */
data class CompetitionRaceAssignmentDto(
    val competitionId: UUID,
    val identifier: String,
    val name: String,
    /** Ob der Ablauf des Wettkampfs überhaupt eine Qualifikationsrunde hat. */
    val hasQualificationRound: Boolean,
    /** Explizit angewähltes Qualifikationsrennen; null = erbt von der Veranstaltung. */
    val raceQualification: UUID?,
    /** Explizit angewähltes Läufe-Rennen; null = erbt von der Veranstaltung. */
    val raceRounds: UUID?,
)

/**
 * Setzt die Zuordnung EINES Rennens neu: [qualificationCompetitions] sind die Wettkämpfe, die
 * dieses Rennen für ihre Qualifikation nutzen, [roundsCompetitions] die für ihre Läufe. Ein
 * Wettkampf, der hier abgewählt wird, aber bisher auf dieses Rennen zeigte, fällt zurück auf „erbt".
 * Ein Wettkampf, der auf ein ANDERES Rennen zeigt, wird durch das Anhaken hierher verschoben — der
 * letzte Klick gewinnt (Entscheidung von Thomas, 11.08.2026).
 */
data class RaceClockerRaceAssignmentsRequest(
    val qualificationCompetitions: List<UUID>,
    val roundsCompetitions: List<UUID>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = RaceClockerRaceAssignmentsRequest(
                qualificationCompetitions = emptyList(),
                roundsCompetitions = emptyList(),
            )
    }
}
