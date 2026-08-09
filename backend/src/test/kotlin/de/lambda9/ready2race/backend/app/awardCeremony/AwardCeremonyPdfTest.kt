package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyLogic
import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyPdf
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySheet
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AwardCeremonyPdfTest {

    // Zwei ausgeschriebene Vereinsnamen aus dem echten Meldebetrieb: genau an ihnen bricht die
    // Personenzeile um, und genau das kann eine Schätzung über die Bootsgröße nicht sehen.
    private val hamburg = "Der Hamburger und Germania Ruder Club von 1836 e.V."
    private val rostock = "Rostocker Ruderclub von 1885 e.V."

    private fun rower(firstName: String, lastName: String, ownClubName: String? = "Ruderclub Nürtingen") =
        AwardCeremonyCandidateParticipant(
            firstName = firstName,
            lastName = lastName,
            role = "Ruderin",
            external = false,
            externalClubName = null,
            ownClubName = ownClubName,
        )

    /** Eine Renngemeinschaft: abwechselnd aus beiden Vereinen, damit die Vereinskette entsteht. */
    private fun mixedCrew(size: Int) = (1..size).map {
        rower("Ruderin$it", "Nachname$it", if (it % 2 == 0) rostock else hamburg)
    }

    private fun crew(size: Int) = (1..size).map { rower("Ruderin$it", "Nachname$it") }

    private fun candidate(
        place: Int,
        participants: List<AwardCeremonyCandidateParticipant> = listOf(rower("Anna", "Meier")),
        time: String? = "4:12,7",
        teamName: String? = "RCN I",
        startNumber: Int = place,
        registeringClubName: String = "Ruderclub Nürtingen",
        penaltySeconds: Int? = null,
        penaltyNote: String? = null,
        roundName: String? = "Finale",
        matchName: String? = "Finale A",
    ) = AwardCeremonyCandidate(
        competitionPlace = place,
        startNumber = startNumber,
        ratingCategoryName = null,
        registeringClubName = registeringClubName,
        teamName = teamName,
        time = time,
        penaltySeconds = penaltySeconds,
        penaltyNote = penaltyNote,
        roundName = roundName,
        matchName = matchName,
        matchTime = null,
        participants = participants,
    )

    private fun sheet(
        identifier: String,
        ratingCategoryName: String?,
        candidates: List<AwardCeremonyCandidate> = listOf(candidate(1), candidate(2), candidate(3)),
        eventDate: String = "15.–16. August 2026",
        eventLocation: String? = "Kiel",
        competitionShortName: String? = "CM 4x+",
    ): AwardCeremonySheet = AwardCeremonyLogic.sheet(
        eventName = "Küstenregatta Kiel",
        eventDate = eventDate,
        eventLocation = eventLocation,
        competitionIdentifier = identifier,
        competitionShortName = competitionShortName,
        competitionName = "Mixed-Coastal-Vierer mit Steuermann",
        ratingCategoryName = ratingCategoryName,
        candidates = candidates,
    )

    /**
     * Drei Boote einer Renngemeinschaft mit abweichendem meldenden Verein - der Aufbau, an dem
     * die frühere Schätzung über die Personenzahl zerbrach. Das dritte Boot trägt eine Zeitstrafe.
     */
    private fun mixedSheet(crewSize: Int, boats: Int = 3): AwardCeremonySheet = sheet(
        "17-NC",
        "Masters A",
        (1..boats).map { place ->
            candidate(
                place,
                participants = mixedCrew(crewSize),
                registeringClubName = "Ruderclub Nürtingen",
                penaltySeconds = if (place == boats) 10 else null,
                penaltyNote = if (place == boats) "Frühstart" else null,
            )
        },
    )

    private fun textOfPage(bytes: ByteArray, page: Int): String =
        Loader.loadPDF(bytes).use { doc ->
            PDFTextStripper().apply {
                startPage = page
                endPage = page
            }.getText(doc)
        }

    private fun linesOfPage(bytes: ByteArray, page: Int): List<String> =
        textOfPage(bytes, page).lines().map { it.trim() }.filter { it.isNotEmpty() }

    private fun pageCount(bytes: ByteArray): Int = Loader.loadPDF(bytes).use { it.numberOfPages }

    private fun occurrences(text: String, part: String): Int = text.split(part).size - 1

    @Test
    fun everyCeremonyGetsExactlyOnePage() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet("17-NC", "Masters A"),
                sheet("17-NC", "Masters B"),
                sheet("18-NC", null),
            )
        )

        assertEquals(3, pageCount(bytes))
    }

    @Test
    fun eachPageCarriesItsOwnHeadings() {
        val bytes = AwardCeremonyPdf.render(listOf(sheet("17-NC", "Masters A"), sheet("18-NC", "Masters B")))

        val first = textOfPage(bytes, 1)
        assertContains(first, "SIEGEREHRUNG")
        assertContains(first, "Küstenregatta Kiel")
        assertContains(first, "17-NC")
        assertContains(first, "Masters A")
        assertFalse(first.contains("Masters B"), "Kategorien dürfen sich nicht über Seiten mischen")

        val second = textOfPage(bytes, 2)
        assertContains(second, "18-NC")
        assertContains(second, "Masters B")
    }

    @Test
    fun theSheetWithoutCategoryHasNoRatingLine() {
        val bytes = AwardCeremonyPdf.render(listOf(sheet("18-NC", null)))

        assertFalse(textOfPage(bytes, 1).contains("Wertung"), "Ohne Kategorie darf keine leere Wertungszeile stehen")
    }

    @Test
    fun namesClubAndTimeReachThePage() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet(
                    "17-NC",
                    "Masters A",
                    listOf(candidate(1, listOf(rower("Anna", "Meier"), rower("Bernd", "Groß", "RG Hansa Kiel")))),
                )
            )
        )

        val text = textOfPage(bytes, 1)
        assertContains(text, "Anna Meier")
        assertContains(text, "Bernd Groß")
        assertContains(text, "RG Hansa Kiel")
        assertContains(text, "4:12,7")
        assertContains(text, "Startnummer 1")
    }

    @Test
    fun aSmallFieldIsSetInTheMostGenerousScale() {
        val sheet = sheet("17-NC", "Masters A")

        assertEquals(1, pageCount(AwardCeremonyPdf.render(listOf(sheet))))
        assertEquals(
            AwardCeremonyPdf.scales.first(),
            AwardCeremonyPdf.scaleFor(sheet),
            "Ein kleines Feld darf nicht ohne Not eng gesetzt werden",
        )
    }

    @Test
    fun threeMixedCoxedFoursFitOnOnePage() {
        val bytes = AwardCeremonyPdf.render(listOf(mixedSheet(crewSize = 4)))

        assertEquals(1, pageCount(bytes))

        // Die Seitenzahl allein würde nicht auffallen lassen, wenn Bronze still verloren ginge.
        val first = textOfPage(bytes, 1)
        assertContains(first, "Startnummer 3")
        assertContains(first, "Zeitstrafe +10 s (Frühstart)")
        assertContains(first, "Meldender Verein: Ruderclub Nürtingen")
    }

    @Test
    fun threeMixedCoxedEightsFitOnOnePage() {
        val bytes = AwardCeremonyPdf.render(listOf(mixedSheet(crewSize = 9)))

        assertEquals(1, pageCount(bytes))

        val first = textOfPage(bytes, 1)
        assertContains(first, "Startnummer 3")
        assertContains(first, "Ruderin9 Nachname9 (Ruderin)")
    }

    @Test
    fun aSharedSecondPlaceWithFourEightsFitsOnOnePage() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet(
                    "17-NC",
                    "Masters A",
                    listOf(
                        candidate(1, participants = crew(9)),
                        candidate(2, startNumber = 2, participants = crew(9)),
                        candidate(2, startNumber = 3, participants = crew(9)),
                        candidate(2, startNumber = 4, participants = crew(9)),
                    ),
                )
            )
        )

        assertEquals(1, pageCount(bytes))

        val first = textOfPage(bytes, 1)
        assertContains(first, "Startnummer 4")
        assertEquals(3, occurrences(first, "geteilter 2. Platz"))
    }

    @Test
    fun oneCommonRaceStandsInTheHeadingOnly() {
        val bytes = AwardCeremonyPdf.render(listOf(sheet("17-NC", "Masters A")))

        assertEquals(
            1,
            occurrences(textOfPage(bytes, 1), "Finale A"),
            "Stammen alle Ränge aus demselben Lauf, steht er einmal im Kopf",
        )
    }

    @Test
    fun differingRacesMoveTheLineIntoEveryBlock() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet(
                    "17-NC",
                    "Masters A",
                    listOf(
                        candidate(1, matchName = "Finale A"),
                        candidate(2, matchName = "Finale A"),
                        candidate(3, matchName = "Finale B"),
                    ),
                )
            )
        )

        val text = textOfPage(bytes, 1)
        // Kein Kopfeintrag: sonst stünde "Finale A" dreimal (Kopf plus zwei Blöcke).
        assertEquals(2, occurrences(text, "Finale A"))
        assertEquals(1, occurrences(text, "Finale B"))
    }

    @Test
    fun aWinnerWithoutARaceDoesNotSwallowTheOthers() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet(
                    "17-NC",
                    "Masters A",
                    listOf(
                        candidate(1, roundName = null, matchName = null),
                        candidate(2),
                        candidate(3),
                    ),
                )
            )
        )

        assertEquals(
            2,
            occurrences(textOfPage(bytes, 1), "Finale A"),
            "Der fehlende Lauf des Siegers darf den der anderen nicht verschlucken",
        )
    }

    @Test
    fun blankHeadingPartsLeaveNoDanglingSeparator() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet(
                    "17-NC",
                    null,
                    listOf(candidate(1)),
                    eventDate = "",
                    eventLocation = "  ",
                    competitionShortName = "",
                )
            )
        )

        val lines = linesOfPage(bytes, 1)
        assertContains(lines, "Küstenregatta Kiel")
        assertContains(lines, "17-NC")
    }

    @Test
    fun aPlannedCeremonyTimeReachesThePage() {
        // AwardCeremonyLogic.sheet setzt das Feld hart auf null - der Zweig lässt sich nur über
        // einen von Hand gebauten Bogen belegen.
        val sheet = AwardCeremonySheet(
            eventName = "Küstenregatta Kiel",
            eventDate = "15.–16. August 2026",
            eventLocation = "Kiel",
            competitionIdentifier = "17-NC",
            competitionShortName = "CM 4x+",
            competitionName = "Mixed-Coastal-Vierer mit Steuermann",
            ratingCategoryName = "Masters A",
            ceremonyTime = LocalDateTime.of(2026, 8, 15, 16, 30),
            ranks = AwardCeremonyLogic.rank(listOf(candidate(1))),
        )

        assertContains(textOfPage(AwardCeremonyPdf.render(listOf(sheet)), 1), "Ehrung: 15.08.2026, 16:30")
    }

    @Test
    fun aSharedRankPrintsItsNumberOnceAndNamesItself() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet(
                    "17-NC",
                    "Masters A",
                    listOf(candidate(1), candidate(2, startNumber = 2), candidate(2, startNumber = 5)),
                )
            )
        )

        val lines = linesOfPage(bytes, 1)
        assertEquals(
            1,
            lines.count { it.startsWith("2.") },
            "Zweimal dieselbe Rangzahl untereinander liest sich wie ein Fehler",
        )
        assertEquals(2, lines.count { it == "geteilter 2. Platz" })
    }

    @Test
    fun aMissingTimeLeavesNoPlaceholderButKeepsThePenalty() {
        val bytes = AwardCeremonyPdf.render(
            listOf(sheet("17-NC", null, listOf(candidate(1, time = null, penaltySeconds = 30, penaltyNote = null))))
        )

        // Rangzahl, Verein und Strafe stehen auf einer Höhe; ohne Zeit bleibt die Stelle leer,
        // statt einen Platzhalter zu tragen.
        assertContains(linesOfPage(bytes, 1), "1. Ruderclub Nürtingen Zeitstrafe +30 s")
    }

    @Test
    fun aBoatWithoutANameShowsOnlyItsStartNumber() {
        val bytes = AwardCeremonyPdf.render(
            listOf(sheet("17-NC", null, listOf(candidate(1, teamName = null))))
        )

        val lines = linesOfPage(bytes, 1)
        assertContains(lines, "Startnummer 1")
        assertFalse(lines.any { it.contains("Boot") }, "Ohne Bootsnamen darf kein leeres „Boot“ stehen")
    }

    @Test
    fun aForeignRegisteringClubGetsItsOwnLine() {
        val bytes = AwardCeremonyPdf.render(
            listOf(
                sheet(
                    "17-NC",
                    null,
                    listOf(candidate(1, registeringClubName = "Ruderverein Meldestelle")),
                )
            )
        )

        assertContains(textOfPage(bytes, 1), "Meldender Verein: Ruderverein Meldestelle")
    }
}
