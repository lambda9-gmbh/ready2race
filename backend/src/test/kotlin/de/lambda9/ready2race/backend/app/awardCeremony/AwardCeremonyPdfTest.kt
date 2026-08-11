package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyLogic
import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyPdf
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySheet
import de.lambda9.ready2race.backend.app.awardCeremony.entity.ResultListOptions
import de.lambda9.ready2race.backend.app.awardCeremony.entity.ResultListSize
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RankedEntry
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RatingCategoryRanking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwardCeremonyPdfTest {

    // Zwei ausgeschriebene Vereinsnamen aus dem echten Meldebetrieb: genau an ihnen bricht die
    // Personenzeile um, und genau das kann eine Schätzung über die Bootsgröße nicht sehen.
    private val hamburg = "Der Hamburger und Germania Ruder Club von 1836 e.V."
    private val rostock = "Rostocker Ruderclub von 1885 e.V."

    private fun rower(firstName: String, lastName: String, ownClubName: String? = "Ruderclub Nürtingen") =
        AwardCeremonyCandidateParticipant(
            firstName = firstName,
            lastName = lastName,
            year = 1990,
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
        ratingCategory = null,
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

    /** Die Rangliste einer Wertung - derselbe Weg, den auch der Service nimmt. */
    private fun sectionOf(candidates: List<AwardCeremonyCandidate>): List<RankedEntry<AwardCeremonyCandidate>> =
        RatingCategoryRanking.groupAndRank(
            items = candidates,
            category = { it.ratingCategory },
            place = { it.competitionPlace },
            tieBreak = { it.startNumber },
        ).let { sections -> if (sections.isEmpty()) emptyList() else sections.single().entries }

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
        entries = sectionOf(candidates),
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

    /**
     * Der Fall, an dem auch die unterste Schriftstufe zerbricht: geteilter zweiter Platz, vier
     * Achter mit Steuermann, jede Mannschaft eine Renngemeinschaft aus zwei ausgeschriebenen
     * Vereinsnamen und dazu ein abweichender meldender Verein.
     */
    private fun sharedSecondWithFourMixedEights(): AwardCeremonySheet = sheet(
        "17-NC",
        "Masters A",
        listOf(
            candidate(1, participants = mixedCrew(9)),
            candidate(2, startNumber = 2, participants = mixedCrew(9)),
            candidate(2, startNumber = 3, participants = mixedCrew(9)),
            candidate(2, startNumber = 4, participants = mixedCrew(9)),
        ),
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

    /** Der Text aller Seiten - für Bögen, die im Aushang-Schriftgrad auf mehrere Blätter wachsen. */
    private fun allText(bytes: ByteArray): String =
        (1..pageCount(bytes)).joinToString("\n") { textOfPage(bytes, it) }

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
    fun aSmallFieldIsSetMoreGenerouslyThanAFullOne() {
        val small = sheet("17-NC", "Masters A")
        val full = mixedSheet(crewSize = 9)

        assertEquals(1, pageCount(AwardCeremonyPdf.render(listOf(small))))

        // Verglichen wird die Position in der Stufenleiter, nicht ein bestimmter Eintrag: eine
        // zusätzliche oder umsortierte Stufe soll den Test weder grundlos rot noch falsch grün
        // machen. Bewacht ist damit weiterhin, dass die Messschleife nicht ohne Not eng setzt.
        assertTrue(
            AwardCeremonyPdf.scales.indexOf(AwardCeremonyPdf.scaleFor(small)) <
                AwardCeremonyPdf.scales.indexOf(AwardCeremonyPdf.scaleFor(full)),
            "Ein kleines Feld darf nicht so eng gesetzt werden wie ein volles",
        )
    }

    @Test
    fun anEmptySelectionFailsInsteadOfProducingAFileWithoutPages() {
        // Der aufrufende Service fängt die leere Auswahl vorher ab; käme sie durch, entstünde ein
        // PDF mit null Seiten, das kein gängiger Betrachter öffnet.
        assertFailsWith<IllegalArgumentException> { AwardCeremonyPdf.render(emptyList()) }
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
        assertContains(first, "Ruderin9 Nachname9 (1990, Ruderin)")
    }

    /**
     * Vier Achter sprengen das Blatt auch auf der untersten Schriftstufe - kleiner wird bewusst
     * nicht gesetzt. Zählt, dass dabei kein Boot verloren geht.
     */
    @Test
    fun aSharedSecondPlaceWithFourEightsRunsOntoASecondPage() {
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

        val pages = pageCount(bytes)
        assertTrue(pages > 1, "Vier Achter passen auf der untersten Stufe nicht auf ein Blatt")

        val whole = (1..pages).joinToString("\n") { textOfPage(bytes, it) }
        assertContains(whole, "Startnummer 4")
        assertEquals(3, occurrences(whole, "geteilter 2. Platz"))
    }

    @Test
    fun anOverflowingCeremonyRepeatsItsFullHeadingOnEveryPage() {
        val bytes = AwardCeremonyPdf.render(listOf(sharedSecondWithFourMixedEights()))

        val pages = pageCount(bytes)
        assertTrue(pages > 1, "Der Aufbau sprengt auch die unterste Stufe")

        (1..pages).forEach { page ->
            val text = textOfPage(bytes, page)
            assertContains(text, "SIEGEREHRUNG", message = "Seite $page trägt keinen Kopf")
            assertContains(text, "17-NC", message = "Seite $page nennt die Rennnummer nicht")
            assertContains(text, "Masters A", message = "Seite $page nennt die Wertung nicht")
        }
    }

    @Test
    fun onlyThePagesAfterTheFirstAreMarkedAsAContinuation() {
        val bytes = AwardCeremonyPdf.render(listOf(sharedSecondWithFourMixedEights()))

        val pages = pageCount(bytes)
        assertFalse(
            textOfPage(bytes, 1).contains("Fortsetzung"),
            "Die erste Seite einer Ehrung ist keine Fortsetzung",
        )
        (2..pages).forEach { page ->
            assertContains(
                textOfPage(bytes, page),
                "Fortsetzung",
                message = "Seite $page wirkt wie eine neue Ehrung",
            )
        }
    }

    @Test
    fun theBreakRunsBetweenRankBlocksAndNeverThroughOne() {
        val bytes = AwardCeremonyPdf.render(listOf(sharedSecondWithFourMixedEights()))

        val pages = pageCount(bytes)
        assertTrue(pages > 1)

        // Je Boot eine Bootszeile mit Startnummer und genau neun Personen. Stimmt das Verhältnis
        // auf jeder Seite, steht keine Mannschaft ohne ihre Vereinszeile - und keine Vereinszeile
        // ohne ihre Mannschaft.
        (1..pages).forEach { page ->
            val text = textOfPage(bytes, page)
            assertEquals(
                occurrences(text, "Startnummer") * 9,
                occurrences(text, "(1990, Ruderin)"),
                "Seite $page zerreißt einen Rangblock",
            )
        }
    }

    /**
     * Eine Fortsetzungsseite kann mitten in einem geteilten Rang beginnen; ihr erster Block ist dann
     * nicht der erste seines Rangs. Ohne die Zusatzregel stünde der Platz auf genau dem Blatt, das
     * den vollen Kopf wiederholt, um für sich allein zu taugen, nur noch im kleinen Vermerk.
     */
    @Test
    fun aContinuationPageOpeningInsideASharedRankStillCarriesTheBigNumber() {
        val bytes = AwardCeremonyPdf.render(listOf(sharedSecondWithFourMixedEights()))

        assertTrue(pageCount(bytes) > 1, "Der Aufbau sprengt auch die unterste Stufe")

        val first = linesOfPage(bytes, 1)
        val second = linesOfPage(bytes, 2)

        // Die Lage, um die es geht, wird zugesichert statt angenommen: steht auf beiden Blättern ein
        // Boot des geteilten zweiten Rangs, läuft der Rang über den Umbruch - und das erste Boot des
        // zweiten Blatts ist nicht das erste seines Rangs.
        assertTrue(first.contains("geteilter 2. Platz"), "Voraussetzung: der geteilte Rang beginnt auf Seite 1")
        assertTrue(second.contains("geteilter 2. Platz"), "Voraussetzung: er läuft auf Seite 2 weiter")

        assertTrue(
            second.any { it.startsWith("2.") },
            "Die Fortsetzungsseite nennt den Platz nur im 8-pt-Vermerk statt in großer Zahl",
        )
    }

    @Test
    fun aCeremonyThatFitsCarriesNoContinuationMark() {
        val bytes = AwardCeremonyPdf.render(listOf(mixedSheet(crewSize = 9)))

        assertEquals(1, pageCount(bytes))
        assertFalse(
            textOfPage(bytes, 1).contains("Fortsetzung"),
            "Der Normalfall bleibt ein Blatt ohne jeden Vermerk",
        )
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
            ranks = AwardCeremonyLogic.ranks(sectionOf(listOf(candidate(1)))),
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

    // --- Ergebnisliste: der Bogen ist nur noch ein Preset des einen Generators -----------------

    /** Der Aushang mit allen Bestandteilen - was die Schalter wegnehmen, prüfen die Fälle unten. */
    private fun postingOptions(
        includeCrew: Boolean = true,
        includeTimes: Boolean = true,
        footerLine: String? = null,
    ) = ResultListOptions(
        heading = "ERGEBNISLISTE",
        includeCrew = includeCrew,
        includeTimes = includeTimes,
        podiumOnly = false,
        byRatingCategory = true,
        size = ResultListSize.POSTING,
        footerLine = footerLine,
    )

    /**
     * Das Abwärtskompatibilitäts-Versprechen, ausgesprochen als Test: der alte Einstieg und das
     * Siegerehrungs-Preset setzen Seite für Seite denselben Text. Zusammen mit den unveränderten
     * Bogen-Tests oben (die den Inhalt selbst festnageln) ist das der Beleg, dass der Umbau die
     * heutige Ausgabe nicht angefasst hat.
     */
    @Test
    fun theCeremonyPresetPrintsTheSamePagesAsTheLegacyEntry() {
        val sheets = listOf(mixedSheet(crewSize = 4), sheet("18-NC", null), sharedSecondWithFourMixedEights())

        val legacy = AwardCeremonyPdf.render(sheets)
        val preset = AwardCeremonyPdf.render(sheets, ResultListOptions.ceremony)

        val pages = pageCount(legacy)
        assertEquals(pages, pageCount(preset))
        (1..pages).forEach { page ->
            assertEquals(textOfPage(legacy, page), textOfPage(preset, page), "Seite $page weicht ab")
        }
    }

    @Test
    fun theHeadingFollowsTheOptions() {
        val bytes = AwardCeremonyPdf.render(listOf(sheet("17-NC", "Masters A")), postingOptions())

        val text = textOfPage(bytes, 1)
        assertContains(text, "ERGEBNISLISTE")
        assertFalse(text.contains("SIEGEREHRUNG"), "Der Aushang ist keine Siegerehrung")
    }

    /** Crew aus: die Namenszeilen verschwinden, Boot und Verein bleiben - und das Blatt wird kürzer. */
    @Test
    fun withoutCrewTheNameLinesDisappear() {
        val sheets = listOf(mixedSheet(crewSize = 9))

        val with = AwardCeremonyPdf.render(sheets, postingOptions())
        val without = AwardCeremonyPdf.render(sheets, postingOptions(includeCrew = false))

        val text = (1..pageCount(without)).joinToString("\n") { textOfPage(without, it) }
        assertEquals(0, occurrences(text, "(1990, Ruderin)"), "Ohne Crew-Aufstellung darf kein Name stehen")
        assertContains(text, "Startnummer 3")

        // Die Zeilenzahl ist die Metrik des Schalters: neun Personen je Boot weniger müssen sich
        // im Umfang niederschlagen, sonst hat der Schalter nur Text versteckt statt Platz geschaffen.
        val linesWith = (1..pageCount(with)).sumOf { linesOfPage(with, it).size }
        val linesWithout = (1..pageCount(without)).sumOf { linesOfPage(without, it).size }
        assertTrue(
            linesWithout < linesWith,
            "Ohne Crew müssen es weniger Zeilen sein ($linesWithout vs. $linesWith)",
        )
    }

    /** Zeiten aus: weder Zeit noch Zeitstrafe - eine Strafe ohne Zeit daneben läse sich wie ein Ergebnis. */
    @Test
    fun withoutTimesNeitherTimeNorPenaltyIsPrinted() {
        val sheets = listOf(mixedSheet(crewSize = 4))

        // Über alle Blätter gelesen: im Aushang-Schriftgrad darf der Bogen auf mehrere Seiten
        // wachsen, und die Zusicherung gilt für jede davon.
        val text = allText(AwardCeremonyPdf.render(sheets, postingOptions(includeTimes = false)))
        assertFalse(text.contains("4:12,7"), "Ohne Zeiten darf keine Zeit stehen")
        assertFalse(text.contains("Zeitstrafe"), "Ohne Zeiten darf auch keine Zeitstrafe stehen")

        // Gegenprobe am selben Bogen: mit Zeiten stehen beide da - sonst bewiese der Fall oben
        // nur, dass die Vorrichtung gar keine Zeiten enthält. Die Strafe wird ohne die Klammer
        // gesucht: in der schmalen Spalte darf der Grund umbrechen.
        val withTimes = allText(AwardCeremonyPdf.render(sheets, postingOptions()))
        assertContains(withTimes, "4:12,7")
        assertContains(withTimes, "Zeitstrafe +10 s")
        assertContains(withTimes, "(Frühstart)")
    }

    /**
     * Der Aushang-Schriftgrad ist eine andere Maßtabelle, kein anderer Text: dasselbe volle Blatt,
     * das der Bogen auf eine Seite presst, braucht als Aushang mehr Platz. Dazu die Metrik an der
     * Tabelle selbst - auch der lesbare Boden der Aushang-Leiter liegt über dem des Bogens, sonst
     * schrumpfte der Aushang unter Druck doch wieder auf Pult-Größe.
     */
    @Test
    fun thePostingSizeSetsVisiblyLargerThanTheCeremonySize() {
        val sheets = listOf(mixedSheet(crewSize = 9))

        val ceremonyPages = pageCount(AwardCeremonyPdf.render(sheets, ResultListOptions.ceremony))
        val postingPages = pageCount(
            AwardCeremonyPdf.render(
                sheets,
                postingOptions().copy(podiumOnly = true),
            )
        )
        assertEquals(1, ceremonyPages, "Voraussetzung: der Bogen presst das volle Blatt auf eine Seite")
        assertTrue(
            postingPages > ceremonyPages,
            "Der Aushang muss größer setzen - gleicher Inhalt, mehr Seiten ($postingPages)",
        )

        val posting = AwardCeremonyPdf.sizesFor(ResultListSize.POSTING)
        val ceremony = AwardCeremonyPdf.sizesFor(ResultListSize.CEREMONY)
        assertTrue(posting.rankNumber > ceremony.rankNumber, "Die Rangzahl trägt den Aushang")
        assertTrue(posting.clubLine > ceremony.clubLine, "Die Vereinszeile trägt den Aushang")
        assertTrue(
            posting.scales.last().nameSize > ceremony.scales.last().nameSize,
            "Auch der lesbare Boden der Aushang-Leiter liegt über dem des Bogens",
        )
    }

    /**
     * Die Fußzeile mit dem Stand gehört auf *jedes* Blatt, auch auf Fortsetzungen: am Brett hängt
     * womöglich nur die zweite Seite noch, und genau der muss man ihr Alter ansehen können.
     */
    @Test
    fun theFooterWithTheStandTimestampReachesEveryPage() {
        val footer = "Küstenregatta Kiel — Stand: 14.08.2026, 17:42"
        val bytes = AwardCeremonyPdf.render(
            listOf(sharedSecondWithFourMixedEights()),
            ResultListOptions.ceremony.copy(footerLine = footer),
        )

        val pages = pageCount(bytes)
        assertTrue(pages > 1, "Der Aufbau sprengt auch die unterste Stufe")
        (1..pages).forEach { page ->
            assertContains(textOfPage(bytes, page), "Stand: 14.08.2026, 17:42", message = "Seite $page trägt keinen Stand")
        }
    }

    /** Ohne Fußzeile in den Optionen bleibt das Blatt frei davon - der Bogen kennt keinen Stand. */
    @Test
    fun withoutAFooterLineNoStandIsPrinted() {
        val bytes = AwardCeremonyPdf.render(listOf(sheet("17-NC", "Masters A")))

        assertFalse(textOfPage(bytes, 1).contains("Stand:"), "Der klassische Bogen trägt keinen Stand")
    }
}
