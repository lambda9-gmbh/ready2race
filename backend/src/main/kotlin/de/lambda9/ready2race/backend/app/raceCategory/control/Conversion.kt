package de.lambda9.ready2race.backend.app.raceCategory.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.raceCategory.entity.RaceCategoryDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceCategoryRecord
import de.lambda9.tailwind.core.KIO
import java.util.*

fun RaceCategoryDto.record() = RaceCategoryRecord(
    id = UUID.randomUUID(),
    name = name,
    description = description
)

fun List<RaceCategoryRecord>.raceCategoryDtoList(): App<Nothing, List<RaceCategoryDto>> = KIO.ok(
    this.map{
        RaceCategoryDto(
            name = it.name!!,
            description = it.description
        )
    }
)