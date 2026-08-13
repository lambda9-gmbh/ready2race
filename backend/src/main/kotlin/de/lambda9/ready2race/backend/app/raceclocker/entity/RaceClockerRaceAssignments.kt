package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

/**
 * Die umgekehrte Sicht auf die RaceClocker-Zuordnung: nicht „welches Rennen nutzt dieser
 * Wettkampf", sondern „welche Wettkämpfe hängen an diesem Rennen". Der Anwender hakt am Rennen die
 * Wettkämpfe an, statt sich durch jeden Wettkampf zu klicken (Wunsch vom 10.08.2026).
 *
 * Geliefert wird die Liste ALLER Wettkämpfe der Veranstaltung mit ihrer aktuellen Anwahl — ein
 * Wettkampf hat genau EIN Rennen für alle seine Runden oder keines. Die Oberfläche entscheidet
 * daraus, welche Kästchen bei welchem Rennen gesetzt sind.
 */
data class CompetitionRaceAssignmentDto(
    val competitionId: UUID,
    val identifier: String,
    val name: String,
    /** Das angewählte Rennen; null = kein Rennen zugewiesen. */
    val race: UUID?,
)

/**
 * Setzt die Zuordnung EINES Rennens neu: [competitions] sind die Wettkämpfe, die dieses Rennen für
 * alle ihre Runden nutzen. Ein Wettkampf, der hier abgewählt wird, aber bisher auf dieses Rennen
 * zeigte, hat danach kein Rennen mehr. Ein Wettkampf, der auf ein ANDERES Rennen zeigt, wird durch
 * das Anhaken hierher verschoben — der letzte Klick gewinnt (Entscheidung von Thomas, 11.08.2026).
 */
data class RaceClockerRaceAssignmentsRequest(
    val competitions: List<UUID>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = RaceClockerRaceAssignmentsRequest(
                competitions = emptyList(),
            )
    }
}
