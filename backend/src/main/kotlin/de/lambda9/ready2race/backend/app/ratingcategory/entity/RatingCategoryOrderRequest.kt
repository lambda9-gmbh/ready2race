package de.lambda9.ready2race.backend.app.ratingcategory.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import java.util.UUID

/**
 * Die gewünschte Reihenfolge der Wertungskategorien einer Veranstaltung, als vollständige Liste
 * von vorne nach hinten. Bewusst die ganze Liste statt „schiebe diese eine Kategorie um einen
 * Platz nach oben": zwei gleichzeitig geöffnete Konfigurationsseiten können damit keine
 * halbvertauschte Reihenfolge hinterlassen.
 *
 * Kategorien, die der Veranstaltung nicht zugeordnet sind, ignoriert der Dienst; zugeordnete
 * Kategorien, die in der Liste fehlen, behalten ihre bisherige Stelle und rutschen ans Ende.
 */
data class RatingCategoryOrderRequest(
    val ratingCategories: List<UUID>,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.Valid

    companion object {
        val example
            get() = RatingCategoryOrderRequest(
                ratingCategories = listOf(UUID.randomUUID(), UUID.randomUUID()),
            )
    }
}
