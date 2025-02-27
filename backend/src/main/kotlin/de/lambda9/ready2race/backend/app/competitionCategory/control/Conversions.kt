package de.lambda9.ready2race.backend.app.competitionCategory.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryDto
import de.lambda9.ready2race.backend.app.competitionCategory.entity.CompetitionCategoryRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionCategoryRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun CompetitionCategoryRequest.toRecord(userId: UUID): App<Nothing, CompetitionCategoryRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            CompetitionCategoryRecord(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        }
    )

fun CompetitionCategoryRecord.competitionCategoryDto(): App<Nothing, CompetitionCategoryDto> = KIO.ok(
    CompetitionCategoryDto(
        id = id,
        name = name,
        description = description
    )
)