package de.lambda9.ready2race.backend.app.raceProperties.control

import de.lambda9.ready2race.backend.app.namedParticipant.entity.NamedParticipantDto
import de.lambda9.ready2race.backend.app.participantCount.entity.ParticipantCountDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesWithNamedParticipantRecord
import java.util.*

fun RacePropertiesDto.record(participantCountId: UUID?) = RacePropertiesRecord(
    id = UUID.randomUUID(),
    identifier = identifier,
    name = name,
    shortName = shortName,
    description = description,
    participantCount = participantCountId,
    participationFee = participationFee,
    rentalFee = rentalFee,
    raceCategory = raceCategory,
)


fun RacePropertiesWithNamedParticipantRecord.namedParticipant() = NamedParticipantDto(
    name = name!!,
    description = description,
    required = required!!,
    participantCount = ParticipantCountDto(
        countMales = countMales!!,
        countFemales = countFemales!!,
        countNonBinary = countNonBinary!!,
        countMixed = countMixed!!
    )
)