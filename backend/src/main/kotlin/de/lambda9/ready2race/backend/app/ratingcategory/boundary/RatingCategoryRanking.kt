package de.lambda9.ready2race.backend.app.ratingcategory.boundary

import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef

/**
 * Ein Ergebnisabschnitt: alle Boote einer Wertungskategorie, in sich gewertet.
 *
 * [category] ist `null` für den Abschnitt „Ohne Wertungskategorie". Der steht immer am Ende, egal
 * welche [RatingCategoryRef.sortOrder] die echten Kategorien tragen.
 */
data class RankedCategory<T>(
    val category: RatingCategoryRef?,
    val entries: List<RankedEntry<T>>,
)

/** Ein Boot mit seinem Platz **innerhalb** seiner Wertungskategorie; `null` heißt ungewertet. */
data class RankedEntry<T>(
    val item: T,
    val categoryPlace: Int?,
)

/**
 * Die eine Stelle, an der ready2race Ergebnisse nach Wertungskategorien trennt und in jedem
 * Abschnitt neu zählt. Öffentliche Ergebnisseite, Schiedsrichter-Dashboard, Athletenanzeige,
 * Platzierungsansicht und Ergebnis-PDF rufen alle hierher — deshalb sehen sie identisch aus.
 *
 * Die Funktion rechnet keine Plätze aus, sie bildet vorhandene Plätze ab. Wer erster, zweiter,
 * dritter im Lauf oder im Wettkampf ist, entscheiden weiterhin die Zeitnahme und
 * `CompetitionExecutionService.computeCompetitionPlaces`.
 */
object RatingCategoryRanking {

    /**
     * @param place Der zugrunde liegende Platz, `null` für ein Boot ohne Wertung — abgemeldet,
     * ausgeschieden, disqualifiziert oder schlicht noch nicht gewertet.
     * @param tieBreak Entscheidet die Reihenfolge zwischen Booten mit demselben Platz und
     * zwischen den ungewerteten; in der Praxis die Startnummer.
     */
    fun <T> groupAndRank(
        items: List<T>,
        category: (T) -> RatingCategoryRef?,
        place: (T) -> Int?,
        tieBreak: (T) -> Int,
    ): List<RankedCategory<T>> = items
        .groupBy { category(it)?.id }
        .map { (_, boats) ->
            val ref = category(boats.first())
            RankedCategory(
                category = ref,
                entries = rankWithinCategory(boats, place, tieBreak),
            )
        }
        // Ein Abschnitt ohne Kategorie sortiert sich nicht gegen die anderen, er hängt hinten an.
        .sortedWith(
            compareBy(
                { it.category == null },
                { it.category?.sortOrder },
                { it.category?.name },
            )
        )

    /**
     * Gleichstände behalten denselben Platz und reißen danach eine Lücke (1, 1, 3) — Boote mit
     * gleichem Ausgangsplatz gibt es wirklich, etwa wenn eine Runde mit
     * `CompetitionSetupPlacesOption.EQUAL` gewertet wird.
     *
     * Ungewertete Boote bekommen keinen Platz und stehen am Ende des Abschnitts, statt zu
     * verschwinden: eine Besatzung, die ihr Boot im Ergebnis nicht findet, hält das für einen
     * Anzeigefehler und nicht für eine Abmeldung.
     */
    private fun <T> rankWithinCategory(
        boats: List<T>,
        place: (T) -> Int?,
        tieBreak: (T) -> Int,
    ): List<RankedEntry<T>> {
        val (ranked, unranked) = boats.partition { place(it) != null }

        val sorted = ranked.sortedWith(compareBy({ place(it)!! }, { tieBreak(it) }))

        var lastPlace: Int? = null
        var lastCategoryPlace = 0

        val rankedEntries = sorted.mapIndexed { index, boat ->
            val categoryPlace = if (place(boat) == lastPlace) {
                lastCategoryPlace
            } else {
                index + 1
            }
            lastPlace = place(boat)
            lastCategoryPlace = categoryPlace

            RankedEntry(boat, categoryPlace)
        }

        return rankedEntries + unranked.sortedBy { tieBreak(it) }.map { RankedEntry(it, null) }
    }
}
