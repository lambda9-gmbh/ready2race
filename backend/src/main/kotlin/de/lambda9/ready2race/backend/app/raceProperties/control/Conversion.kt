package de.lambda9.ready2race.backend.app.raceProperties.control

import de.lambda9.ready2race.backend.app.racePropertiesHasNamedParticipant.entity.NamedParticipantWithCountDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesWithNamedParticipantRecord
import java.util.*

fun RacePropertiesDto.record(raceId: UUID?, raceTemplateId: UUID?) = RacePropertiesRecord(
    id = UUID.randomUUID(),
    race = raceId,
    raceTemplate = raceTemplateId,
    identifier = identifier,
    name = name,
    shortName = shortName,
    description = description,
    countMales = countMales,
    countFemales = countFemales,
    countNonBinary = countNonBinary,
    countMixed = countMixed,
    participationFee = participationFee,
    rentalFee = rentalFee,
    raceCategory = raceCategory,
)


fun RacePropertiesWithNamedParticipantRecord.namedParticipant() = NamedParticipantWithCountDto(
    name = name!!,
    description = description,
    required = required!!,
    countMales = countMales!!,
    countFemales = countFemales!!,
    countNonBinary = countNonBinary!!,
    countMixed = countMixed!!
)