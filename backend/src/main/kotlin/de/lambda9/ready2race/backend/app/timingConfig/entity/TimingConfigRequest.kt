package de.lambda9.ready2race.backend.app.timingConfig.entity

import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import java.util.UUID

/**
 * Die Zeitnahme-Konfiguration eines Wettkampfs. Jedes Feld ist optional: die RaceClocker-Rennen
 * entstehen dort erst kurz vor der Regatta, die Konfiguration muss also unvollstaendig speicherbar
 * sein. Woran es fehlt, zeigt die Oberflaeche im Zeitnahme- und im Durchfuehrungs-Tab.
 */
data class TimingConfigRequest(
    val timingSystem: TimingSystem?,
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
    val startlistConfigQualification: UUID?,
    val startlistConfigRounds: UUID?,
    val resultImportConfig: UUID?,
) : Validatable {

    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            validateUrl(timeTrialResultsUrl, "timeTrialResultsUrl"),
            validateUrl(heatsResultsUrl, "heatsResultsUrl"),
        )

    companion object {

        /**
         * Hier abgelehnt statt erst beim Abholen, damit ein Tippfehler beim Bearbeiten auffaellt und
         * nicht mitten in der Regatta. Nutzt dieselbe Normalisierung wie der Pull: der Host ist auf
         * RaceClocker festgenagelt, ein fehlendes Schema wird ergaenzt -- so sieht eine URL aus, die
         * aus der Adresszeile kopiert wurde.
         */
        private fun validateUrl(value: String?, field: String): ValidationResult {
            if (value.isNullOrBlank()) return ValidationResult.Valid

            return if (RaceClockerFeed.normalizeUrl(value).unsafeRunSync().getOrNull() == null) {
                ValidationResult.Invalid.Message { "$field must be a URL on raceclocker.com" }
            } else {
                ValidationResult.Valid
            }
        }

        val example
            get() = TimingConfigRequest(
                timingSystem = TimingSystem.RACECLOCKER,
                timeTrialResultsUrl = "https://www.raceclocker.com/7ffb822a",
                heatsResultsUrl = "https://www.raceclocker.com/7c854955",
                startlistConfigQualification = UUID.randomUUID(),
                startlistConfigRounds = UUID.randomUUID(),
                resultImportConfig = UUID.randomUUID(),
            )
    }
}
