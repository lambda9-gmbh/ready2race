package de.lambda9.ready2race.backend.app.ratingcategory.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection

data class RatingCategoriesToEventRequest(
    val ratingCategories: List<RatingCategoryToEventRequest>
) : Validatable {
    override fun validate(): ValidationResult =
        ValidationResult.allOf(
            this::ratingCategories validate collection
        )

    companion object {
        val example
            get() = RatingCategoriesToEventRequest(
                ratingCategories = listOf(RatingCategoryToEventRequest.example),
            )
    }
}
