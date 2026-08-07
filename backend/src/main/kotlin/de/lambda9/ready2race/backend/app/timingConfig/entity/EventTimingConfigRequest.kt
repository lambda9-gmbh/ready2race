package de.lambda9.ready2race.backend.app.timingConfig.entity

import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull

/**
 * Zeitnahme-Voreinstellung der Veranstaltung. Jedes Feld optional wie beim Wettkampf
 * ([TimingConfigRequest]): die RaceClocker-Rennen entstehen erst kurz vor der Regatta.
 */
data class EventTimingConfigRequest(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
) : Validatable {

    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            validateUrl(timeTrialResultsUrl, "timeTrialResultsUrl"),
            validateUrl(heatsResultsUrl, "heatsResultsUrl"),
        )

    companion object {

        /** Dieselbe Regel wie [TimingConfigRequest.validateUrl] - Tippfehler sollen beim Bearbeiten auffallen. */
        private fun validateUrl(value: String?, field: String): ValidationResult {
            if (value.isNullOrBlank()) return ValidationResult.Valid

            return if (RaceClockerFeed.normalizeUrl(value).unsafeRunSync().getOrNull() == null) {
                ValidationResult.Invalid.Message { "$field must be a URL on raceclocker.com" }
            } else {
                ValidationResult.Valid
            }
        }

        val example
            get() = EventTimingConfigRequest(
                timingSystem = TimingSystem.RACECLOCKER,
                timeTrialResultsUrl = "https://www.raceclocker.com/7ffb822a",
                heatsResultsUrl = "https://www.raceclocker.com/7c854955",
            )
    }
}
