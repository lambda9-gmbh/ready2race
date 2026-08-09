package de.lambda9.ready2race.backend.app.ratingcategory.entity


data class RatingCategoryToEventDto(
    val ratingCategory: RatingCategoryDto,
    val yearFrom: Int?,
    val yearTo: Int?,
    /**
     * Die Stelle dieser Kategorie in den Ergebnisabschnitten der Veranstaltung, aufsteigend ab 0.
     * Sie hängt an der Zuordnung und nicht an der Kategorie: dieselbe Kategorie darf bei zwei
     * Regatten unterschiedlich einsortiert sein.
     */
    val sortOrder: Int,
)