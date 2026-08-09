package de.lambda9.ready2race.backend.app.ratingcategory.entity

import java.util.UUID

/**
 * Die Wertungskategorie eines Bootes, so knapp wie eine Ergebnisliste sie braucht: zum Gruppieren
 * die [id], zum Anzeigen der [name], zum Sortieren der Abschnitte [sortOrder] aus
 * `event_rating_category`.
 *
 * Bewusst nicht [RatingCategoryDto]: das trägt die Beschreibung, aber keine Reihenfolge, weil es
 * die Kategorie ohne Bezug zu einer Veranstaltung beschreibt — und genau dort liegt die
 * Reihenfolge.
 */
data class RatingCategoryRef(
    val id: UUID,
    val name: String,
    val sortOrder: Int,
)
