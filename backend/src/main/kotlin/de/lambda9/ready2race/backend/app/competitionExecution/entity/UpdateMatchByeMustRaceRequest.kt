package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult

/**
 * Schaltet an einem Freilos-Lauf "muss gefahren werden" um (competition_match.bye_must_race).
 *
 * Fairness-Regel mancher Regelwerke: Auch ein Boot ohne Gegner fährt die Strecke. Die Platzierung
 * bleibt Freilos-Semantik (das Boot steigt unabhängig von Zeit und Platz auf), die Zeit wird
 * genommen und "außer Konkurrenz" angezeigt.
 */
data class UpdateMatchByeMustRaceRequest(
    val mustRace: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example = UpdateMatchByeMustRaceRequest(
            mustRace = true,
        )
    }
}
