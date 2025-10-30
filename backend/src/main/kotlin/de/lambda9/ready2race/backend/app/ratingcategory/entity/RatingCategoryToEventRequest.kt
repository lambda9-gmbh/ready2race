package de.lambda9.ready2race.backend.app.ratingcategory.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.max
import de.lambda9.ready2race.backend.validation.validators.IntValidators.min
import java.util.*

data class RatingCategoryToEventRequest(
    val ratingCategory: UUID,
    val yearFrom: Int?,
    val yearTo: Int?,
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::yearFrom validate min(1900),
            this::yearFrom validate max(2100),
        )

    companion object {
        val example
            get() = RatingCategoryToEventRequest(
                ratingCategory = UUID.randomUUID(),
                yearFrom = 1970,
                yearTo = 2010,
            )
    }
}
