package de.lambda9.ready2race.backend.app.documentTemplate

import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapDocumentTemplatePackage
import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapDocumentTemplateService
import de.lambda9.ready2race.backend.app.documentTemplate.control.GapDocumentTemplateFontRepo
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateError
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateFontRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.text.TextAlign
import de.lambda9.ready2race.testing.testComprehension
import de.lambda9.tailwind.jooq.Jooq
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull


/**
 * Export und Import einer Urkundenvorlage gegen die echte Datenbank. Das Paketformat selbst ist in
 * [GapDocumentTemplatePackageTest] ohne Datenbank abgedeckt; hier geht es um das, was erst im
 * Zusammenspiel sichtbar wird: dass eine gespeicherte Vorlage vollständig ins Paket wandert, dass
 * ein Paket wieder zu derselben Vorlage wird, und dass der Import an denselben Prüfungen scheitert
 * wie ein normaler Upload — der ganze Grund, warum er über `createTemplate` läuft.
 */
class GapDocumentTemplateServiceTest {

    private fun pdfBytes(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private val placeholders = listOf(
        GapDocumentPlaceholderRequest(
            name = null,
            type = GapDocumentPlaceholderType.PLACE,
            page = 1,
            relLeft = 0.0,
            relTop = 0.447,
            relWidth = 1.0,
            relHeight = 0.04,
            textAlign = TextAlign.CENTER,
            fontSize = 20,
            bold = true,
            italic = false,
            staticText = null,
        ),
        GapDocumentPlaceholderRequest(
            name = "Unterzeichner",
            type = GapDocumentPlaceholderType.FREE_TEXT,
            page = 1,
            relLeft = 0.1,
            relTop = 0.92,
            relWidth = 0.8,
            relHeight = 0.03,
            textAlign = TextAlign.LEFT,
            fontSize = null,
            bold = false,
            italic = true,
            staticText = "Moritz Petri — Vorsitzender",
        ),
    )

    private fun request(fontName: String? = "TheSansOffice") = GapDocumentTemplateRequest(
        type = GapDocumentType.AWARD_CERTIFICATE,
        placeholders = placeholders,
        fontName = fontName,
    )

    @Test
    fun aStoredTemplateExportsAsAReadablePackage() = testComprehension {
        val pdf = pdfBytes()
        !GapDocumentTemplateService.addTemplate(File("urkunde.pdf", pdf), request(), null)

        val id = !Jooq.query { selectFrom(GAP_DOCUMENT_TEMPLATE).fetchSingle().id!! }
        val exported = !GapDocumentTemplateService.exportTemplate(id)

        assertEquals("urkunde.r2rtpl.zip", exported.name)

        val result = GapDocumentTemplatePackage.read(exported.bytes)
        val content = assertIs<GapDocumentTemplatePackage.ReadResult.Ok>(result).content

        assertEquals("urkunde.pdf", content.name)
        assertEquals(GapDocumentType.AWARD_CERTIFICATE, content.request.type)
        assertEquals("TheSansOffice", content.request.fontName)
        assertContentEquals(pdf, content.pdf)
        assertNull(content.font)
        // Jedes Platzhalter-Feld muss die Runde überstehen, nicht nur Typ und Position - eine
        // verlorene Schriftgröße oder ein verlorener fester Text fiele sonst erst beim Druck auf.
        assertEquals(placeholders.toSet(), content.request.placeholders.toSet())
    }

    @Test
    fun theStoredFontTravelsInThePackage() = testComprehension {
        !GapDocumentTemplateService.addTemplate(File("urkunde.pdf", pdfBytes()), request(), null)
        val id = !Jooq.query { selectFrom(GAP_DOCUMENT_TEMPLATE).fetchSingle().id!! }

        // Die Schrift wird am Service vorbei eingesetzt: `addTemplate` prüft sie mit PDFBox, und
        // eine echte einbettbare Schriftdatei liegt dem Repository nicht bei. Für Export und
        // Font-Download zählt nur, dass Name und Bytes unverändert durchgereicht werden.
        !GapDocumentTemplateFontRepo.upsert(
            GapDocumentTemplateFontRecord(
                template = id,
                fileName = "TheSansOffice.otf",
                data = byteArrayOf(1, 2, 3),
            )
        )

        val exported = !GapDocumentTemplateService.exportTemplate(id)
        val content = assertIs<GapDocumentTemplatePackage.ReadResult.Ok>(
            GapDocumentTemplatePackage.read(exported.bytes)
        ).content

        assertEquals("TheSansOffice.otf", content.font?.name)
        assertContentEquals(byteArrayOf(1, 2, 3), content.font?.bytes)
    }

    @Test
    fun aPackageImportsIntoAnEqualTemplate() = testComprehension {
        val pdf = pdfBytes()
        val pkg = GapDocumentTemplatePackage.write(
            GapDocumentTemplatePackage.Content(
                name = "urkunde.pdf",
                request = request(),
                pdf = pdf,
                font = null,
            )
        )

        val created = !GapDocumentTemplateService.importTemplate(File("urkunde.r2rtpl.zip", pkg))
        val id = assertIs<ApiResponse.Created>(created).id

        // Der Rundlauf: was aus dem Import herauskommt, muss sich wieder identisch exportieren
        // lassen - das ist die Zusage, mit der ein Paket zwischen zwei Instanzen wandert.
        val exported = !GapDocumentTemplateService.exportTemplate(id)
        val content = assertIs<GapDocumentTemplatePackage.ReadResult.Ok>(
            GapDocumentTemplatePackage.read(exported.bytes)
        ).content

        assertEquals("urkunde.pdf", content.name)
        assertEquals(GapDocumentType.AWARD_CERTIFICATE, content.request.type)
        assertEquals("TheSansOffice", content.request.fontName)
        assertContentEquals(pdf, content.pdf)
        assertEquals(placeholders.toSet(), content.request.placeholders.toSet())
    }

    @Test
    fun importCreatesASecondTemplateInsteadOfOverwriting() = testComprehension {
        val pkg = GapDocumentTemplatePackage.write(
            GapDocumentTemplatePackage.Content(
                name = "urkunde.pdf",
                request = request(),
                pdf = pdfBytes(),
                font = null,
            )
        )

        !GapDocumentTemplateService.importTemplate(File("urkunde.r2rtpl.zip", pkg))
        !GapDocumentTemplateService.importTemplate(File("urkunde.r2rtpl.zip", pkg))

        assertEquals(2, !Jooq.query { fetchCount(GAP_DOCUMENT_TEMPLATE) })
    }

    @Test
    fun anUnreadablePackageIsRejected() = testComprehension {
        assertKIOFails(GapDocumentTemplateError.InvalidPackage) {
            GapDocumentTemplateService.importTemplate(File("kaputt.zip", byteArrayOf(1, 2, 3, 4)))
        }

        assertEquals(0, !Jooq.query { fetchCount(GAP_DOCUMENT_TEMPLATE) })
    }

    @Test
    fun aPackageFromANewerVersionIsRejected() = testComprehension {
        val manifest = """
            {"formatVersion":2,"name":"urkunde.pdf","type":"AWARD_CERTIFICATE","fontName":null,
             "fontFile":null,"placeholders":[]}
        """.trimIndent().toByteArray()

        assertKIOFails(GapDocumentTemplateError.UnsupportedPackageVersion) {
            GapDocumentTemplateService.importTemplate(
                File("neu.r2rtpl.zip", zipOf("template.json" to manifest, "template.pdf" to pdfBytes()))
            )
        }
    }

    /**
     * Der Kern von Task 1: der Import geht durch dieselben Prüfungen wie ein Upload. Wäre das nicht
     * so, käme eine Vorlage in die Datenbank, deren "PDF" keines ist - und sie fiele erst auf, wenn
     * jemand vor der Siegerehrung Urkunden drucken will.
     */
    @Test
    fun aPackageWhoseTemplateIsNoPdfIsRejected() = testComprehension {
        val pkg = GapDocumentTemplatePackage.write(
            GapDocumentTemplatePackage.Content(
                name = "urkunde.pdf",
                request = request(),
                pdf = "kein pdf".toByteArray(),
                font = null,
            )
        )

        assertKIOFails(GapDocumentTemplateError.InvalidPdf) {
            GapDocumentTemplateService.importTemplate(File("urkunde.r2rtpl.zip", pkg))
        }

        assertEquals(0, !Jooq.query { fetchCount(GAP_DOCUMENT_TEMPLATE) })
    }

    @Test
    fun aPackageWithAPlaceholderTypeForeignToItsTypeIsRejected() = testComprehension {
        val pkg = GapDocumentTemplatePackage.write(
            GapDocumentTemplatePackage.Content(
                name = "teilnahme.pdf",
                request = GapDocumentTemplateRequest(
                    // PLACE gibt es nur auf der Siegerurkunde, nicht auf der Teilnahmeurkunde.
                    type = GapDocumentType.CERTIFICATE_OF_PARTICIPATION,
                    placeholders = placeholders,
                    fontName = null,
                ),
                pdf = pdfBytes(),
                font = null,
            )
        )

        assertKIOFails(GapDocumentTemplateError.PlaceholderTypeNotSupported) {
            GapDocumentTemplateService.importTemplate(File("teilnahme.r2rtpl.zip", pkg))
        }
    }

    @Test
    fun theFontDownloadDeliversTheStoredFile() = testComprehension {
        !GapDocumentTemplateService.addTemplate(File("urkunde.pdf", pdfBytes()), request(), null)
        val id = !Jooq.query { selectFrom(GAP_DOCUMENT_TEMPLATE).fetchSingle().id!! }

        !GapDocumentTemplateFontRepo.upsert(
            GapDocumentTemplateFontRecord(
                template = id,
                fileName = "TheSansOffice.otf",
                data = byteArrayOf(4, 5, 6),
            )
        )

        val font = !GapDocumentTemplateService.downloadFont(id)

        assertEquals("TheSansOffice.otf", font.name)
        assertContentEquals(byteArrayOf(4, 5, 6), font.bytes)
    }

    @Test
    fun theFontDownloadFailsWithoutAStoredFont() = testComprehension {
        !GapDocumentTemplateService.addTemplate(File("urkunde.pdf", pdfBytes()), request(), null)
        val id = !Jooq.query { selectFrom(GAP_DOCUMENT_TEMPLATE).fetchSingle().id!! }

        assertKIOFails(GapDocumentTemplateError.NotFound) {
            GapDocumentTemplateService.downloadFont(id)
        }
    }

    @Test
    fun exportingAnUnknownTemplateFails() = testComprehension {
        assertKIOFails(GapDocumentTemplateError.NotFound) {
            GapDocumentTemplateService.exportTemplate(UUID.randomUUID())
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
