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
) {
    companion object {
        /**
         * Die Sortierstelle einer Kategorie, die an einer Meldung hängt, der Veranstaltung aber
         * nie zugeordnet wurde. Kein Randfall: im Bestand der CRF 2026 tragen 32 Boote
         * „Internationale Wertung" und 55 „Deutsche Meisterschaft Wertung", ohne dass eine dieser
         * Kategorien in `event_rating_category` steht.
         *
         * Solche Kategorien gehören ans Ende, nicht an den Anfang. Bis zum 09.08.2026 stand hier
         * 0 — damit drängte sich eine nie konfigurierte Kategorie vor jede gepflegte Reihenfolge,
         * in der laufenden Anwendung sofort sichtbar. Untereinander sortieren sie sich nach Namen;
         * der Abschnitt „Ohne Wertungskategorie" bleibt trotzdem der allerletzte.
         */
        const val UNCONFIGURED_SORT_ORDER = Int.MAX_VALUE
    }
}
