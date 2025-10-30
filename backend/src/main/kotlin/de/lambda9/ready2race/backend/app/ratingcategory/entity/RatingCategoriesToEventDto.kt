package de.lambda9.ready2race.backend.app.ratingcategory.entity


data class RatingCategoryToEventDto(
    val ratingCategory: RatingCategoryDto,
    val yearFrom: Int?,
    val yearTo: Int?,
)