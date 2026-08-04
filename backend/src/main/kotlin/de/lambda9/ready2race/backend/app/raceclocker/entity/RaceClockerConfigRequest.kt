package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull

/**
 * The two public RaceClocker results URLs of a competition. Both are optional: a competition without a
 * qualification round needs no time trial URL, and until the URLs are filled in the results simply
 * keep arriving via the spreadsheet upload.
 */
data class RaceClockerConfigRequest(
    val timeTrialResultsUrl: String?,
    val heatsResultsUrl: String?,
) : Validatable {

    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            validateUrl(timeTrialResultsUrl, "timeTrialResultsUrl"),
            validateUrl(heatsResultsUrl, "heatsResultsUrl"),
        )

    companion object {

        /**
         * Rejected here rather than only at pull time, so a typo surfaces while editing instead of
         * mid-regatta. Uses the same normalisation the pull does, which pins the host to RaceClocker but
         * accepts an input without scheme - that is how a URL looks when copied out of a browser bar.
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
            get() = RaceClockerConfigRequest(
                timeTrialResultsUrl = "https://www.raceclocker.com/7ffb822a",
                heatsResultsUrl = "https://www.raceclocker.com/7c854955",
            )
    }
}
