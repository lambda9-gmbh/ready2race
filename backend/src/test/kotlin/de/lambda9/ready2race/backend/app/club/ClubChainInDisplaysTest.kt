package de.lambda9.ready2race.backend.app.club

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.certificate.boundary.AwardCertificateService
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateMode
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.app.club.boundary.ClubNameKey
import de.lambda9.ready2race.backend.app.club.control.ClubShortNameRepo
import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapDocumentTemplateService
import de.lambda9.ready2race.backend.app.documentTemplate.entity.AssignGapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.app.eventInfo.boundary.EventInfoService
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubShortNameRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.text.TextAlign
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die Vereinskette am echten Postgres - Athleten-Anzeige und Urkunde.
 *
 * Die Ableitung selbst ist in [ClubCompositionTest] ohne Datenbank festgeschrieben. Was sich dort
 * nicht prüfen lässt und genau hier schiefgeht, sind die Abfragen: der Verein einer Person hängt
 * an einem *zweiten*, aliasierten CLUB-Join, während der bisherige CLUB-Join weiterhin den
 * meldenden Verein liefert. Verwechselt man die beiden, sieht der Code richtig aus und die Anzeige
 * zeigt trotzdem für jedes Boot den Verein, der es angemeldet hat.
 *
 * Die gemeldete Mannschaft ist deshalb absichtlich so gebaut, dass der meldende Verein in KEINER
 * der beiden Ketten vorkommen darf.
 */
class ClubChainInDisplaysTest {

    @Test
    fun theAthleteBoardShowsTheClubsTheAthletesWearInsteadOfOneMixedTeamTerm() = testComprehension {
        val seeded = seedClubChain()

        // Eine gepflegte Kurzform, die die Heuristik nicht erraten könnte ("Mainzer RV") - so ist
        // belegt, dass die Anzeige club_short_name wirklich heranzieht.
        !ClubShortNameRepo.upsert(
            ClubShortNameRecord(
                nameKey = ClubNameKey.of(MAINZ),
                sampleName = MAINZ,
                shortName = "Mainz",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )

        val board = (!EventInfoService.getAthleteBoard(seeded.eventId)).dto
        val team = board.running.single().teams.single()

        assertEquals(EXPECTED_FULL, team.clubsFull)
        assertEquals(
            "Mainz / Marburger RV / RK Flensburg / RC Nürtingen / Rostocker RC",
            team.clubsShort,
        )

        // Der Kern des Ganzen: der meldende Verein steht nirgends, und "Renngemeinschaft" ist weg.
        assertFalse(team.clubsFull!!.contains("Kieler"), "meldender Verein in der Kette: ${team.clubsFull}")
        assertFalse(team.clubsShort!!.contains("Renngemeinschaft"))
    }

    @Test
    fun theAwardCertificateCarriesTheWholeChainWithoutAnyShortening() = testComprehension {
        val seeded = seedClubChain()

        // Auch eine gepflegte Kurzform darf die Urkunde nicht erreichen - sie zeigt ausdrücklich
        // immer die vollen Vereinsnamen.
        !ClubShortNameRepo.upsert(
            ClubShortNameRecord(
                nameKey = ClubNameKey.of(MAINZ),
                sampleName = MAINZ,
                shortName = "Mainz",
                createdAt = CHAIN_SEED_TIME,
                updatedAt = CHAIN_SEED_TIME,
            )
        )
        assignAwardCertificateTemplate()

        val file = !AwardCertificateService.downloadForCompetition(
            eventId = seeded.eventId,
            competitionId = seeded.competitionId,
            options = AwardCertificateOptions(
                maxPlace = 3,
                mode = AwardCertificateMode.PER_TEAM,
                withBackground = false,
            ),
            format = AwardCertificateService.Format.PDF,
        )

        val raw = pdfText(file.bytes)
        assertChainSurvivedTheLineBreak(raw)
    }

    /**
     * Dieselbe Urkunde als DOCX. Der DOCX-Renderer legt jede Zeile in einen eigenen Rahmen; bis der
     * Umbruch für beide Formate an einer Stelle entstand, brach hier Word nach eigenen Maßen um und
     * mitten durch Vereinsnamen, während das PDF gar nicht umbrach. Deshalb steht der Fall auch für
     * dieses Format da - der Vertrag über die Zeilenzahl selbst hängt in
     * [de.lambda9.ready2race.backend.pdf.GapDocumentGeometryContractTest].
     */
    @Test
    fun theSameCertificateAsDocxCarriesTheSameChain() = testComprehension {
        val seeded = seedClubChain()
        assignAwardCertificateTemplate()

        val file = !AwardCertificateService.downloadForCompetition(
            eventId = seeded.eventId,
            competitionId = seeded.competitionId,
            options = AwardCertificateOptions(
                maxPlace = 3,
                mode = AwardCertificateMode.PER_TEAM,
                withBackground = false,
            ),
            format = AwardCertificateService.Format.DOCX,
        )

        val document = XWPFDocument(ByteArrayInputStream(file.bytes))
        val framedLines = document.paragraphs.filter { it.ctp.pPr?.framePr != null }.map { it.text }
        document.close()

        assertTrue(framedLines.size > 1, "Die Kette hätte umgebrochen werden müssen: $framedLines")
        assertChainSurvivedTheLineBreak(framedLines.joinToString("\n"))
    }

    /**
     * Was von der Kette nach dem Umbruch zu erwarten ist, für beide Formate gleich: jeder Verein
     * steht vollständig da (keiner ist am Umbruch zerrissen), die Reihenfolge im Boot bleibt, und
     * gekürzt wird nichts.
     */
    private fun assertChainSurvivedTheLineBreak(rendered: String) {
        val text = rendered.replace(Regex("""\s+"""), " ")

        EXPECTED_CLUBS.forEach {
            assertTrue(text.contains(it), "Verein fehlt oder ist am Umbruch zerrissen: '$it' in: $text")
        }
        EXPECTED_CLUBS.zipWithNext().forEach { (before, after) ->
            assertTrue(
                text.indexOf(before) < text.indexOf(after),
                "'$before' müsste vor '$after' stehen: $text",
            )
        }

        assertTrue(
            rendered.trim().lines().size > 1,
            "Die Kette hätte umgebrochen werden müssen, steht aber auf einer Zeile: $rendered",
        )

        assertFalse(text.contains("Renngemeinschaft"), text)
        assertFalse(text.contains("Mainzer RV"), "gekürzt statt voll ausgeschrieben: $text")
        assertFalse(text.contains("Kieler"), "meldender Verein auf der Urkunde: $text")
    }

    /**
     * Eine A4-Vorlage mit genau einem Platzhalter - dem Vereinsnamen -, über die volle Breite und
     * so groß, wie eine echte Urkunde ihn setzt.
     */
    private fun TestComprehensionScope<JEnv>.assignAwardCertificateTemplate() {
        !GapDocumentTemplateService.addTemplate(
            File("urkunde.pdf", emptyA4Pdf()),
            GapDocumentTemplateRequest(
                type = GapDocumentType.AWARD_CERTIFICATE,
                placeholders = listOf(
                    GapDocumentPlaceholderRequest(
                        name = null,
                        type = GapDocumentPlaceholderType.CLUB_NAME,
                        page = 1,
                        relLeft = 0.0,
                        relTop = 0.5,
                        relWidth = 1.0,
                        relHeight = 0.04,
                        textAlign = TextAlign.CENTER,
                        fontSize = 18,
                        bold = false,
                        italic = false,
                        staticText = null,
                    )
                ),
                fontName = null,
            ),
            null,
        )

        val templateId = !Jooq.query { selectFrom(GAP_DOCUMENT_TEMPLATE).fetchSingle().id!! }
        !GapDocumentTemplateService.assignTemplate(
            GapDocumentType.AWARD_CERTIFICATE,
            AssignGapDocumentTemplateRequest(templateId),
        )
    }

    private fun emptyA4Pdf(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun pdfText(bytes: ByteArray): String {
        val doc = Loader.loadPDF(bytes)
        val text = PDFTextStripper().getText(doc)
        doc.close()
        return text
    }
}
