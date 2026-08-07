package de.lambda9.ready2race.backend.app.documentTemplate.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Das Austauschformat einer Lückentext-Vorlage: ein ZIP aus `template.json` (Metadaten und
 * Platzhalter, feldgleich mit [GapDocumentTemplateRequest]), `template.pdf` und optional der
 * Schriftdatei unter ihrem eigenen Dateinamen. Bewusst ohne Datenbank und ohne KIO, damit Lesen
 * und Schreiben unmittelbar testbar bleiben; die Übersetzung in Fehlerantworten macht der Service.
 */
object GapDocumentTemplatePackage {

    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "template.json"
    const val PDF_ENTRY = "template.pdf"

    /** Obergrenze je entpacktem Eintrag, damit ein kleines Archiv nicht beliebig viel Speicher wird. */
    const val MAX_ENTRY_BYTES = 20 * 1024 * 1024

    data class Manifest(
        val formatVersion: Int,
        val name: String,
        val type: GapDocumentType,
        val fontName: String?,
        val fontFile: String?,
        val placeholders: List<GapDocumentPlaceholderRequest>,
    )

    data class Content(
        val name: String,
        val request: GapDocumentTemplateRequest,
        val pdf: ByteArray,
        val font: File?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Content

            if (name != other.name) return false
            if (request != other.request) return false
            if (!pdf.contentEquals(other.pdf)) return false
            if (font != other.font) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + request.hashCode()
            result = 31 * result + pdf.contentHashCode()
            result = 31 * result + (font?.hashCode() ?: 0)
            return result
        }
    }

    sealed interface ReadResult {
        data class Ok(val content: Content) : ReadResult
        data object Invalid : ReadResult
        data object UnsupportedVersion : ReadResult
    }

    fun write(content: Content): ByteArray {
        val manifest = Manifest(
            formatVersion = FORMAT_VERSION,
            name = content.name,
            type = content.request.type,
            fontName = content.request.fontName,
            fontFile = content.font?.name,
            placeholders = content.request.placeholders,
        )

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(jsonMapper.writeValueAsBytes(manifest))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(PDF_ENTRY))
            zip.write(content.pdf)
            zip.closeEntry()

            content.font?.let { font ->
                zip.putNextEntry(ZipEntry(font.name))
                zip.write(font.bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): ReadResult {
        val entries = mutableMapOf<String, ByteArray>()

        try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val data = zip.readAtMost(MAX_ENTRY_BYTES) ?: return ReadResult.Invalid
                    if (!entry.isDirectory && isSafeEntryName(entry.name)) {
                        entries[entry.name] = data
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (_: Exception) {
            return ReadResult.Invalid
        }

        val manifestBytes = entries[MANIFEST_ENTRY] ?: return ReadResult.Invalid

        val manifest = try {
            jsonMapper.readValue<Manifest>(manifestBytes)
        } catch (_: Exception) {
            return ReadResult.Invalid
        }

        if (manifest.formatVersion != FORMAT_VERSION) {
            return ReadResult.UnsupportedVersion
        }

        val pdf = entries[PDF_ENTRY] ?: return ReadResult.Invalid

        val font = manifest.fontFile?.let { fileName ->
            val data = entries[fileName] ?: return ReadResult.Invalid
            File(fileName, data)
        }

        return ReadResult.Ok(
            Content(
                name = manifest.name,
                request = GapDocumentTemplateRequest(
                    type = manifest.type,
                    placeholders = manifest.placeholders,
                    fontName = manifest.fontName,
                ),
                pdf = pdf,
                font = font,
            )
        )
    }

    /** Nur flache Einträge; alles mit Pfadanteil kommt aus einem präparierten Archiv. */
    private fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != ".."

    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
