package de.lambda9.ready2race.backend.app.webDAV.entity

import java.util.*

data class WebDAVImportOptionsDto(
    val data: List<WebDAVExportType>,
    val events: List<WebDAVImportOptionsEventDto>
) {
    data class WebDAVImportOptionsEventDto(
        val eventId: UUID,
        val eventFolderName: String,
        val competitions: List<WebDAVImportOptionsCompetitionDto>
    )

    data class WebDAVImportOptionsCompetitionDto(
        val competitionId: UUID,
        val competitionFolderName: String,
    )
}