package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.calls.requests.logger
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.file.File
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

data class ManifestExport(
    val exportDateTime: LocalDateTime,
    val version: String,
    val exportedTypes: List<WebDAVExportType>,
    val exportedEvents: List<ManifestEventExport>
) {
    data class ManifestEventExport(
        val eventId: UUID,
        val folderName: String,
        val competitions: List<ManifestCompetitionExport>
    )

    data class ManifestCompetitionExport(
        val competitionId: UUID,
        val folderName: String
    )

    companion object {
        private const val EXPORT_VERSION = "1.0.0"

        fun createExportFile(
            exportedTypes: List<WebDAVExportType>,
            exportedEvents: List<ManifestEventExport>
        ): App<WebDAVError.WebDAVExternError, File> = KIO.comprehension {
            val manifest = ManifestExport(
                exportDateTime = LocalDateTime.now(),
                version = EXPORT_VERSION,
                exportedTypes = exportedTypes,
                exportedEvents = exportedEvents,
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