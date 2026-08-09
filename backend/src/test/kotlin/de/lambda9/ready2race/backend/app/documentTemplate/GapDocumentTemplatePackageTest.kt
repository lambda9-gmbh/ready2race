package de.lambda9.ready2race.backend.app.documentTemplate

import de.lambda9.ready2race.backend.app.documentTemplate.boundary.GapDocumentTemplatePackage
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.text.TextAlign
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GapDocumentTemplatePackageTest {

    private val placeholder = GapDocumentPlaceholderRequest(
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
    )

    private val pdfBytes = byteArrayOf(37, 80, 68, 70)

    private fun content(font: File? = null) = GapDocumentTemplatePackage.Content(
        name = "urkunde-vorlage.pdf",
        request = GapDocumentTemplateRequest(
            type = GapDocumentType.AWARD_CERTIFICATE,
            placeholders = listOf(placeholder),
            fontName = "TheSansOffice",
        ),
        pdf = pdfBytes,
        font = font,
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun roundTripWithoutFont() {
        val result = GapDocumentTemplatePackage.read(GapDocumentTemplatePackage.write(content()))

        val ok = assertIs<GapDocumentTemplatePackage.ReadResult.Ok>(result)
        assertEquals("urkunde-vorlage.pdf", ok.content.name)
        assertEquals(GapDocumentType.AWARD_CERTIFICATE, ok.content.request.type)
        assertEquals("TheSansOffice", ok.content.request.fontName)
        assertEquals(listOf(placeholder), ok.content.request.placeholders)
        assertContentEquals(pdfBytes, ok.content.pdf)
        assertNull(ok.content.font)
    }

    @Test
    fun roundTripWithFont() {
        val font = File("TheSansOffice.otf", byteArrayOf(1, 2, 3))

        val result = GapDocumentTemplatePackage.read(GapDocumentTemplatePackage.write(content(font)))

        val ok = assertIs<GapDocumentTemplatePackage.ReadResult.Ok>(result)
        assertEquals("TheSansOffice.otf", ok.content.font?.name)
        assertContentEquals(byteArrayOf(1, 2, 3), ok.content.font?.bytes)
    }

    @Test
    fun brokenArchiveIsRejected() {
        val result = GapDocumentTemplatePackage.read(byteArrayOf(1, 2, 3, 4, 5))

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun archiveWithoutManifestIsRejected() {
        val result = GapDocumentTemplatePackage.read(zipOf("template.pdf" to pdfBytes))

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun archiveWithoutPdfIsRejected() {
        val full = GapDocumentTemplatePackage.write(content())
        val manifest = readEntry(full, "template.json")

        val result = GapDocumentTemplatePackage.read(zipOf("template.json" to manifest))

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun unreadableManifestIsRejected() {
        val result = GapDocumentTemplatePackage.read(
            zipOf(
                "template.json" to "kein json".toByteArray(),
                "template.pdf" to pdfBytes,
            )
        )

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun unknownFormatVersionIsRejected() {
        val manifest = """
            {"formatVersion":2,"name":"x.pdf","type":"AWARD_CERTIFICATE","fontName":null,
             "fontFile":null,"placeholders":[]}
        """.trimIndent().toByteArray()

        val result = GapDocumentTemplatePackage.read(
            zipOf("template.json" to manifest, "template.pdf" to pdfBytes)
        )

        assertIs<GapDocumentTemplatePackage.ReadResult.UnsupportedVersion>(result)
    }

    @Test
    fun fontEntryWithDirectoryComponentIsRejected() {
        // Ein Eintrag mit Pfadanteil wird nicht gelesen; die im Manifest angekündigte Schrift
        // fehlt damit, und das Paket ist unbrauchbar statt halb übernommen.
        val manifest = """
            {"formatVersion":1,"name":"x.pdf","type":"AWARD_CERTIFICATE","fontName":"X",
             "fontFile":"../evil.otf","placeholders":[]}
        """.trimIndent().toByteArray()

        val result = GapDocumentTemplatePackage.read(
            zipOf(
                "template.json" to manifest,
                "template.pdf" to pdfBytes,
                "../evil.otf" to byteArrayOf(9),
            )
        )

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun entryOverTheSingleEntryLimitIsRejected() {
        // Ein Eintrag mit lauter Nullen komprimiert auf fast nichts, entpackt aber auf die volle
        // Größe — der Test bleibt trotz der 20+ MB schnell.
        val validPackage = GapDocumentTemplatePackage.write(content())
        val manifest = readEntry(validPackage, "template.json")
        val pdf = readEntry(validPackage, "template.pdf")
        val oversized = ByteArray(GapDocumentTemplatePackage.MAX_ENTRY_BYTES + 1)

        val result = GapDocumentTemplatePackage.read(
            zipOf(
                "template.json" to manifest,
                "template.pdf" to pdf,
                "oversized.bin" to oversized,
            )
        )

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    @Test
    fun entriesSummingOverTheTotalLimitAreRejected() {
        // Jeder Eintrag für sich bleibt unter MAX_ENTRY_BYTES; erst die Summe reißt die
        // Gesamtgrenze. Ebenfalls Nullen, damit das Archiv klein und der Test schnell bleibt.
        val validPackage = GapDocumentTemplatePackage.write(content())
        val manifest = readEntry(validPackage, "template.json")
        val pdf = readEntry(validPackage, "template.pdf")

        val perEntry = GapDocumentTemplatePackage.MAX_ENTRY_BYTES - 1024 * 1024
        val entryCount = (GapDocumentTemplatePackage.MAX_TOTAL_BYTES / perEntry) + 2
        val entries = (0 until entryCount)
            .map { "entry-$it" to ByteArray(perEntry) }
            .toTypedArray()

        val result = GapDocumentTemplatePackage.read(
            zipOf(
                "template.json" to manifest,
                "template.pdf" to pdf,
                *entries,
            )
        )

        assertIs<GapDocumentTemplatePackage.ReadResult.Invalid>(result)
    }

    private fun readEntry(zipBytes: ByteArray, name: String): ByteArray {
        java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        error("entry $name not found")
    }
}
