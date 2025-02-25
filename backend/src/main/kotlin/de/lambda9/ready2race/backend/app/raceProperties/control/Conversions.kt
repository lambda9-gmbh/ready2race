package de.lambda9.ready2race.backend.app.raceProperties.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.raceProperties.entity.NamedParticipantForRaceDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.NamedParticipantForRaceRequestDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesDto
import de.lambda9.ready2race.backend.app.raceProperties.entity.RacePropertiesRequestDto
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantForRacePropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RacePropertiesRecord
import de.lambda9.tailwind.core.KIO
import java.util.*

fun RacePropertiesRequestDto.toRecord(raceId: UUID?, raceTemplateId: UUID?) = RacePropertiesRecord(
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

fun NamedParticipantForRaceRequestDto.toRecord(propertiesId: UUID) = RacePropertiesHasNamedParticipantRecord(
    raceProperties = propertiesId,
    namedParticipant = namedParticipant,
    required = required,
    countMales = countMales,
    countFemales = countFemales,
    countNonBinary = countNonBinary,
    countMixed = countMixed
)

fun NamedParticipantForRacePropertiesRecord.toDto(): App<Nothing, NamedParticipantForRaceDto> = KIO.ok(
    NamedParticipantForRaceDto(
        id = id!!,
        name = name!!,
        description = description,
        required = required!!,
        countMales = countMales!!,
        countFemales = countFemales!!,
        countNonBinary = countNonBinary!!,
        countMixed = countMixed!!
    )
)

fun NamedParticipantForRacePropertiesRecord.applyRacePropertiesHasNamedParticipant(racePropertiesId: UUID, namedParticipantId: UUID): App<Nothing, RacePropertiesHasNamedParticipantRecord> = KIO.ok(
    RacePropertiesHasNamedParticipantRecord(
        raceProperties = racePropertiesId,
        namedParticipant = namedParticipantId,
        required = required!!,
        countMales = countMales!!,
        countFemales = countFemales!!,
        countNonBinary = countNonBinary!!,
        countMixed = countMixed!!
    )
)

fun RacePropertiesRequestDto.toUpdateFunction(): RacePropertiesRecord.() -> Unit = let {
    {
        identifier = it.identifier
        name = it.name
        shortName = it.shortName
        description = it.description
        countMales = it.countMales
        countFemales = it.countFemales
        countNonBinary = it.countNonBinary
        countMixed = it.countMixed
        participationFee = it.participationFee
        rentalFee = it.rentalFee
        raceCategory = it.raceCategory
    }
}