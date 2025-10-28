package de.lambda9.ready2race.backend.app.webDAV.entity

import de.lambda9.ready2race.backend.app.competition.entity.CompetitionForExportDto
import java.util.*

data class DataExportEventStatusDto(
    val id: UUID?,
    val name: String?,
    val dataExports: List<WebDAVExportType>,
    val competitions: List<CompetitionForExportDto?>
)