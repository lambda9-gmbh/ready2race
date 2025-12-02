package de.lambda9.ready2race.backend.app.matchResultImportConfig.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigDto
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigRequest
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigDto
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.MatchResultImportConfigRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistExportConfigRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun MatchResultImportConfigRequest.toRecord(userId: UUID): App<Nothing, MatchResultImportConfigRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        MatchResultImportConfigRecord(
            id = UUID.randomUUID(),
            name = name,
            colTeamStartNumber = colTeamStartNumber,
            colTeamPlace = colTeamPlace,
            colTeamTime = colTeamTime,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)

fun MatchResultImportConfigRecord.toDto(): App<Nothing, MatchResultImportConfigDto> = KIO.ok(
    MatchResultImportConfigDto(
        id = id,
        name = name,
        colTeamStartNumber = colTeamStartNumber,
        colTeamPlace = colTeamPlace,
        colTeamTime = colTeamTime,
    )
)