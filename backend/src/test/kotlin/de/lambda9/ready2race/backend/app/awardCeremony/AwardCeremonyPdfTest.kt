package de.lambda9.ready2race.backend.app.awardCeremony

import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyLogic
import de.lambda9.ready2race.backend.app.awardCeremony.boundary.AwardCeremonyPdf
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidate
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyCandidateParticipant
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySheet
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AwardCeremonyPdfTest {

    private fun rower(firstName: String, lastName: String, ownClubName: String? = "Ruderclub Nürtingen") =
        AwardCeremonyCandidateParticipant(
            firstName = firstName,
            lastName = lastName,
            role = "Ruderin",
            external = false,
            externalClubName = null,
            ownClubName = ownClubName,
        )

    private fun candidate(
        place: Int,
        participants: List<AwardCeremonyCandidateParticipant> = listOf(rower("Anna", "Meier")),
        time: String? = "4:12,7",
        teamName: String? = "RCN I",
    ) = AwardCeremonyCandidate(
        competitionPlace = place,
        startNumber = place,
        ratingCategoryName = null,
        registeringClubName = "Ruderclub Nürtingen",
        teamName = teamName,
        time = time,
        penaltySeconds = null,
        penaltyNote = null,
        roundName = "Finale",
        matchName = "Finale A",
        matchTime = null,
        participants = participants,
    )

    private fun sheet(
        identifier: String,
        ratingCategoryName: String?,
        candidates: List<AwardCeremonyCandidate> = listOf(candidate(1), candidate(2), candidate(3)),
    ): AwardCeremonySheet = AwardCeremonyLogic.sheet(
        eventName = "Küstenregatta Kiel",
        eventDate = "15.–16. August 2026",
        eventLocation = "Kiel",
        competitionIdentifier = identifier,
        competitionShortName = "CM 4x+",
        competitionName = "Mixed-Coastal-Vierer mit Steuermann",
        ratingCategoryName = ratingCategoryName,
        candidates = candidates,
    )

    private fun textOfPage(bytes: ByteArray, page: Int): String =
        Loader.loadPDF(bytes).use { doc ->
            PDFTextStripper().apply {
                startPage = page
                endPage = page
            }.getText(doc)
        }

    private fun pageCount(bytes: ByteArray): Int = Loader.loadPDF(bytes).use { it.numberOfPages }

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
    fun aFullFieldOfEightsStillFitsOnOnePage() {
        val eight = (1..9).map { rower("Ruderin$it", "Nachname$it") }
        val bytes = AwardCeremonyPdf.render(
            listOf(sheet("17-NC", null, listOf(candidate(1, eight), candidate(2, eight), candidate(3, eight))))
        )

        assertEquals(1, pageCount(bytes))
    }
}
