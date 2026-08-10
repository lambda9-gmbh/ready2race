package de.lambda9.ready2race.backend.app.ratingcategory

import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RatingCategoryRanking
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRef
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RatingCategoryRankingTest {

    private data class Boat(
        val name: String,
        val place: Int?,
        val startNumber: Int,
        val category: RatingCategoryRef?,
    )

    private val meisterschaft = RatingCategoryRef(UUID.randomUUID(), "Meisterschaften", 0)
    private val breitensport = RatingCategoryRef(UUID.randomUUID(), "Breitensport", 1)
    private val masters = RatingCategoryRef(UUID.randomUUID(), "Masters", 2)

    private fun rank(boats: List<Boat>) = RatingCategoryRanking.groupAndRank(
        items = boats,
        category = { it.category },
        place = { it.place },
        tieBreak = { it.startNumber },
    )

    private fun boat(
        name: String,
        place: Int?,
        category: RatingCategoryRef?,
        startNumber: Int = 1,
    ) = Boat(name = name, place = place, startNumber = startNumber, category = category)

    @Test
    fun sectionsFollowTheConfiguredSortOrder() {
        val result = rank(
            listOf(
                boat("a", 1, masters),
                boat("b", 2, meisterschaft),
                boat("c", 3, breitensport),
            )
        )

        assertEquals(
            listOf("Meisterschaften", "Breitensport", "Masters"),
            result.map { it.category?.name },
        )
    }

    @Test
    fun sortOrderBeatsAlphabet() {
        // "Masters" steht vor "Breitensport", weil die Veranstaltung es so konfiguriert hat -
        // eine alphabetische Sortierung wuerde hier die umgekehrte Reihenfolge liefern.
        val early = RatingCategoryRef(UUID.randomUUID(), "Masters", 0)
        val late = RatingCategoryRef(UUID.randomUUID(), "Breitensport", 1)

        val result = rank(listOf(boat("a", 1, late), boat("b", 2, early)))

        assertEquals(listOf("Masters", "Breitensport"), result.map { it.category?.name })
    }

    @Test
    fun equalSortOrderFallsBackToName() {
        val a = RatingCategoryRef(UUID.randomUUID(), "Zander", 7)
        val b = RatingCategoryRef(UUID.randomUUID(), "Aal", 7)

        val result = rank(listOf(boat("a", 1, a), boat("b", 2, b)))

        assertEquals(listOf("Aal", "Zander"), result.map { it.category?.name })
    }

    @Test
    fun boatsWithoutCategoryFormTheirOwnSectionAtTheEnd() {
        // Die kategorielose Gruppe steht auch dann hinten, wenn eine echte Kategorie eine
        // hoehere sortOrder traegt.
        val result = rank(
            listOf(
                boat("ohne", 1, null),
                boat("mit", 2, masters),
            )
        )

        assertEquals(listOf("Masters", null), result.map { it.category?.name })
        assertEquals(listOf("mit"), result[0].entries.map { it.item.name })
        assertEquals(listOf("ohne"), result[1].entries.map { it.item.name })
    }

    @Test
    fun eachSectionStartsCountingAtOne() {
        val result = rank(
            listOf(
                boat("m1", 1, meisterschaft),
                boat("b1", 2, breitensport),
                boat("m2", 3, meisterschaft),
                boat("b2", 4, breitensport),
                boat("b3", 5, breitensport),
            )
        )

        assertEquals(listOf(1, 2), result[0].entries.map { it.categoryPlace })
        assertEquals(listOf("m1", "m2"), result[0].entries.map { it.item.name })
        assertEquals(listOf(1, 2, 3), result[1].entries.map { it.categoryPlace })
        assertEquals(listOf("b1", "b2", "b3"), result[1].entries.map { it.item.name })
    }

    @Test
    fun tiedPlacesShareTheCategoryPlaceAndLeaveAGap() {
        // Standard competition ranking: zwei geteilte Erste, danach der Dritte - kein Zweiter.
        val result = rank(
            listOf(
                boat("a", 4, meisterschaft, startNumber = 1),
                boat("b", 4, meisterschaft, startNumber = 2),
                boat("c", 6, meisterschaft, startNumber = 3),
            )
        )

        assertEquals(listOf(1, 1, 3), result[0].entries.map { it.categoryPlace })
    }

    @Test
    fun tiesAreCountedPerSectionNotAcrossSections() {
        val result = rank(
            listOf(
                boat("m1", 1, meisterschaft),
                boat("m2", 1, meisterschaft),
                boat("b1", 3, breitensport),
                boat("b2", 4, breitensport),
            )
        )

        assertEquals(listOf(1, 1), result[0].entries.map { it.categoryPlace })
        assertEquals(listOf(1, 2), result[1].entries.map { it.categoryPlace })
    }

    @Test
    fun unrankedBoatsKeepNoPlaceAndSitAtTheEndOfTheirSection() {
        // Abgemeldet, ausgeschieden, disqualifiziert oder schlicht noch nicht gewertet: alle
        // kommen ohne Platz an und duerfen die Zaehlung der gewerteten Boote nicht verschieben.
        val result = rank(
            listOf(
                boat("dnf", null, meisterschaft, startNumber = 5),
                boat("erster", 2, meisterschaft, startNumber = 3),
                boat("abgemeldet", null, meisterschaft, startNumber = 1),
                boat("zweiter", 9, meisterschaft, startNumber = 4),
            )
        )

        val entries = result[0].entries
        assertEquals(listOf("erster", "zweiter", "abgemeldet", "dnf"), entries.map { it.item.name })
        assertEquals(listOf(1, 2), entries.take(2).map { it.categoryPlace })
        assertTrue(entries.drop(2).all { it.categoryPlace == null })
    }

    @Test
    fun aSectionOfOnlyUnrankedBoatsSurvivesWithoutPlaces() {
        val result = rank(listOf(boat("a", null, breitensport), boat("b", null, breitensport)))

        assertEquals(1, result.size)
        assertEquals("Breitensport", result[0].category?.name)
        assertTrue(result[0].entries.all { it.categoryPlace == null })
    }

    @Test
    fun categoriesWithoutBoatsProduceNoSection() {
        val result = rank(listOf(boat("a", 1, meisterschaft)))

        assertEquals(1, result.size)
        assertEquals("Meisterschaften", result[0].category?.name)
    }

    @Test
    fun anEmptyFieldProducesNoSections() {
        assertEquals(emptyList(), rank(emptyList()))
    }

    @Test
    fun boatsAreOrderedByPlaceWithinASectionRegardlessOfInputOrder() {
        val result = rank(
            listOf(
                boat("dritter", 8, breitensport, startNumber = 3),
                boat("erster", 2, breitensport, startNumber = 1),
                boat("zweiter", 5, breitensport, startNumber = 2),
            )
        )

        assertEquals(listOf("erster", "zweiter", "dritter"), result[0].entries.map { it.item.name })
        assertEquals(listOf(1, 2, 3), result[0].entries.map { it.categoryPlace })
    }

    @Test
    fun tiedBoatsAreOrderedByTieBreak() {
        val result = rank(
            listOf(
                boat("hohe", 1, masters, startNumber = 9),
                boat("niedrige", 1, masters, startNumber = 2),
            )
        )

        assertEquals(listOf("niedrige", "hohe"), result[0].entries.map { it.item.name })
    }

    @Test
    fun singleCategoryBehavesLikeTheOldFlatRanking() {
        // Der haeufigste Fall einer Regatta ohne Wertungskategorien: ein Abschnitt, dessen
        // Platzierung sich von der bisherigen gemeinsamen Rangliste nicht unterscheidet.
        val result = rank(
            listOf(
                boat("a", 1, null, startNumber = 1),
                boat("b", 2, null, startNumber = 2),
                boat("c", 3, null, startNumber = 3),
            )
        )

        assertEquals(1, result.size)
        assertNull(result[0].category)
        assertEquals(listOf(1, 2, 3), result[0].entries.map { it.categoryPlace })
    }

    @Test
    fun anUnconfiguredCategorySortsBehindEveryConfiguredOne() {
        // Am 09.08.2026 in der laufenden Anwendung aufgefallen: der Bestand traegt Kategorien an
        // den Meldungen, ohne dass sie je einer Veranstaltung zugeordnet wurden. Mit der alten
        // Ersatzstelle 0 drueckte sich so eine Kategorie vor jede gepflegte Reihenfolge.
        val unkonfiguriert = RatingCategoryRef(
            UUID.randomUUID(),
            "Internationale Wertung",
            RatingCategoryRef.UNCONFIGURED_SORT_ORDER,
        )

        val result = rank(
            listOf(
                boat("a", 1, unkonfiguriert),
                boat("b", 2, masters),
                boat("c", 3, meisterschaft),
                boat("d", 4, null),
            )
        )

        assertEquals(
            listOf("Meisterschaften", "Masters", "Internationale Wertung", null),
            result.map { it.category?.name },
        )
    }

    @Test
    fun unconfiguredCategoriesSortAmongThemselvesByName() {
        val zuerst = RatingCategoryRef(UUID.randomUUID(), "Deutsche Meisterschaft Wertung", RatingCategoryRef.UNCONFIGURED_SORT_ORDER)
        val danach = RatingCategoryRef(UUID.randomUUID(), "Internationale Wertung", RatingCategoryRef.UNCONFIGURED_SORT_ORDER)

        val result = rank(listOf(boat("a", 1, danach), boat("b", 2, zuerst)))

        assertEquals(
            listOf("Deutsche Meisterschaft Wertung", "Internationale Wertung"),
            result.map { it.category?.name },
        )
    }
}
