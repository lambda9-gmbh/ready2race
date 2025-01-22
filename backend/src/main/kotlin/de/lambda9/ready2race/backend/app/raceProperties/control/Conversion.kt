package de.lambda9.ready2race.backend.app.raceProperties.control

import de.lambda9.ready2race.backend.app.raceProperties.entity.NamedParticipantForRaceDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.NamedParticipantForRaceRequestDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantForRacePropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesRecord
import java.util.*

fun RacePropertiesRequestDto.record(raceId: UUID?, raceTemplateId: UUID?) = RacePropertiesRecord(
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

fun NamedParticipantForRaceRequestDto.record(propertiesId: UUID) = RacePropertiesHasNamedParticipantRecord(
    raceProperties = propertiesId,
    namedParticipant = namedParticipant,
    required = required,
    countMales = countMales,
    countFemales = countFemales,
    countNonBinary = countNonBinary,
    countMixed = countMixed
)

fun NamedParticipantForRacePropertiesRecord.toDto() = NamedParticipantForRaceDto(
    name = name!!,
    description = description,
    required = required!!,
    countMales = countMales!!,
    countFemales = countFemales!!,
    countNonBinary = countNonBinary!!,
    countMixed = countMixed!!
)