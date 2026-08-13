package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.util.UUID

data class RaceClockerRaceDto(
    val id: UUID,
    val name: String,
    val resultsUrl: String,
    val capturesLaps: Boolean,
    val position: Int,
)

/**
 * Ein Rennen, so wie ein Lauf es braucht: Adresse zum Holen, Name für die Fehlermeldung.
 *
 * Der Name ist kein Schmuck. „Lauf im Rennen Kurzstrecke nicht gefunden" ist am Renntag brauchbar,
 * eine nackte URL nicht.
 */
data class RaceClockerRaceRef(
    val id: UUID,
    val name: String,
    val resultsUrl: String,
)

data class RaceClockerRaceRequest(
    val name: String,
    val resultsUrl: String,
    val capturesLaps: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::name validate notBlank,
        this::resultsUrl validate notBlank,
    )

    companion object {
        val example
            get() = RaceClockerRaceRequest(
                name = "Kurzstrecke",
                resultsUrl = "https://www.raceclocker.com/7c854955",
                capturesLaps = false,
            )
    }
}
