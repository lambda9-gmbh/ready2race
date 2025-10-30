package de.lambda9.ready2race.backend.app.ratingcategory.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryDto
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryRequest
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryToEventDto
import de.lambda9.ready2race.backend.app.ratingcategory.entity.RatingCategoryToEventRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRatingCategoryRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRatingCategoryViewRecord
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

fun RatingCategoryToEventRequest.toRecord(event: UUID, userId: UUID): App<Nothing, EventRatingCategoryRecord> = KIO.ok(
    EventRatingCategoryRecord(
        event = event,
        ratingCategory = ratingCategory,
        yearRestrictionFrom = yearFrom,
        yearRestrictionTo = yearTo,
        createdAt = LocalDateTime.now(),
        createdBy = userId,
        updatedAt = LocalDateTime.now(),
        updatedBy = userId,
    )
)

fun EventRatingCategoryViewRecord.toDto(): App<Nothing, RatingCategoryToEventDto> = KIO.ok(
    RatingCategoryToEventDto(
        ratingCategory = RatingCategoryDto(
            id = ratingCategory!!,
            name = ratingCategoryName!!,
            description = ratingCategoryDescription,
        ),
        yearFrom = yearRestrictionFrom,
        yearTo = yearRestrictionTo,
    )
)