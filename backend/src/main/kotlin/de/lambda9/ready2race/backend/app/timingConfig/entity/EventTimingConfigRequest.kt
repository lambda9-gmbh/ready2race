package de.lambda9.ready2race.backend.app.timingConfig.entity

import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

/**
 * Zeitnahme-Voreinstellung der Veranstaltung. Jedes Feld optional wie beim Wettkampf
 * ([TimingConfigRequest]): die RaceClocker-Rennen entstehen erst kurz vor der Regatta.
 */
data class EventTimingConfigRequest(
    val timingSystem: TimingSystem?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
    /**
     * Der automatische Abruf und seine Takte. Anders als die Felder darüber nicht optional: Sie
     * haben in der Datenbank eine Vorgabe (Migration V202608071600), und ein `null` hier hieße
     * "unverändert lassen" - eine Bedeutung, die das Formular nicht braucht und die beim Ausschalten
     * der Automatik gefährlich wäre.
     */
    val autoPull: Boolean,
    val intervalActiveSeconds: Int,
    val intervalUpcomingSeconds: Int,
    val watchBeforeMinutes: Int,
    val watchAfterMinutes: Int,
) : Validatable {

    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            validateInterval(intervalActiveSeconds, "intervalActiveSeconds"),
            validateInterval(intervalUpcomingSeconds, "intervalUpcomingSeconds"),
            validateMinutes(watchBeforeMinutes, "watchBeforeMinutes"),
            validateMinutes(watchAfterMinutes, "watchAfterMinutes"),
        )

    companion object {

        /**
         * Dieselbe Grenze, die der Job ohnehin erzwingt - hier nur, damit sie beim Speichern
         * sichtbar wird statt still zu greifen.
         */
        private fun validateInterval(value: Int, field: String): ValidationResult =
            if (value < RaceClockerPollLogic.MIN_INTERVAL_SECONDS) {
                ValidationResult.Invalid.Message { "$field must be at least ${RaceClockerPollLogic.MIN_INTERVAL_SECONDS} seconds" }
            } else {
                ValidationResult.Valid
            }

        /** Null Minuten sind erlaubt: "erst ab der geplanten Startzeit beobachten" ist eine Ansage. */
        private fun validateMinutes(value: Int, field: String): ValidationResult =
            if (value < 0) {
                ValidationResult.Invalid.Message { "$field must not be negative" }
            } else {
                ValidationResult.Valid
            }

        val example
            get() = EventTimingConfigRequest(
                timingSystem = TimingSystem.RACECLOCKER,
                startlistConfigQualification = UUID.randomUUID(),
                startlistConfigRounds = UUID.randomUUID(),
                resultImportConfig = UUID.randomUUID(),
                autoPull = true,
                intervalActiveSeconds = 5,
                intervalUpcomingSeconds = 60,
                watchBeforeMinutes = 15,
                watchAfterMinutes = 120,
            )
    }
}
