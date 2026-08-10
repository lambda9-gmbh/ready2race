package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import java.util.UUID

/**
 * Wie ein RaceClocker-Rennen gestartet wird.
 *
 * Nur [INDIVIDUAL] hat in RaceClocker einen echten Countdown; [WAVE] ist der Modus, in den ein
 * Rennen selbsttätig kippt, sobald beim Import eine Spalte auf „Lauf" gemappt wird. Der Unterschied
 * steht hier, weil der Bediener in ihm denkt — und weil sich daran künftig prüfen lässt, ob das
 * gewählte Startlisten-Preset zum Rennen passt.
 */
enum class RaceClockerStartMode { INDIVIDUAL, WAVE }

data class RaceClockerRaceDto(
    val id: UUID,
    val name: String,
    val resultsUrl: String,
    val startMode: RaceClockerStartMode,
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
    val startMode: RaceClockerStartMode,
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
                startMode = RaceClockerStartMode.WAVE,
                capturesLaps = false,
            )
    }
}
