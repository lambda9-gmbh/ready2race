package de.lambda9.ready2race.backend.app.results.control

import de.lambda9.ready2race.backend.app.results.entity.CompetitionChoiceDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionHavingResultsRecord

fun CompetitionHavingResultsRecord.toDto() = CompetitionChoiceDto(
    id = id!!,
    identifier = identifier!!,
    name = name!!,
    shortName = shortName,
    category = category,
)