package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime

data class ManifestExport(
    val exportDateTime: LocalDateTime,
    val version: String,
    val exportedTypes: List<WebDAVExportType>
) {
    companion object {
        private const val EXPORT_VERSION = "1.0.0"

        fun createExportFile(
            exportedTypes: List<WebDAVExportType>
        ): App<WebDAVError.WebDAVExternError, File> = KIO.comprehension {
            val manifest = ManifestExport(
                exportDateTime = LocalDateTime.now(),
                version = EXPORT_VERSION,
                exportedTypes = exportedTypes
            )

            try {
                val json = jsonMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(manifest)
                    .toByteArray()
                KIO.ok(File(name = "manifest.json", bytes = json))
            } catch (e: Exception) {
                logger.error(e) { "Failed to create manifest.json" }
                KIO.fail(WebDAVError.ManifestSerializationFailed)
            }
        }
    }
}