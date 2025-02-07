package de.lambda9.ready2race.backend.app.raceCategory.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceCategoryRecord
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun RaceCategoryRequest.toRecord(userId: UUID): App<Nothing, RaceCategoryRecord> =
    KIO.ok(
        LocalDateTime.now().let { now ->
            RaceCategoryRecord(
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

fun List<RaceCategoryRecord>.raceCategoryDtoList(): App<Nothing, List<RaceCategoryDto>> = KIO.ok(
    this.map{
        RaceCategoryDto(
            id = it.id,
            name = it.name,
            description = it.description
        )
    }
)