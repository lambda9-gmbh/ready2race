package de.lambda9.ready2race.backend.app.ratingcategory.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.RatingCategoryRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.UUID

fun RatingCategoryRequest.toRecord(userId: UUID): App<Nothing, RatingCategoryRecord> = KIO.ok(
    LocalDateTime.now().let { now ->
        RatingCategoryRecord(
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

fun RatingCategoryRecord.toDto(): App<Nothing, RatingCategoryDto> = KIO.ok(
    RatingCategoryDto(
        id = id,
        name = name,
        description = description,
    )
)