package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionProperties.entity.CompetitionPropertiesRequestDto
import de.lambda9.ready2race.backend.app.competitionProperties.entity.NamedParticipantForCompetitionDto
import de.lambda9.ready2race.backend.app.competitionProperties.entity.NamedParticipantForCompetitionRequestDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesHasNamedParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionPropertiesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.NamedParticipantForCompetitionPropertiesRecord
import de.lambda9.tailwind.core.KIO
import java.util.*

fun CompetitionPropertiesRequestDto.toRecord(competitionId: UUID?, competitionTemplateId: UUID?) = CompetitionPropertiesRecord(
    id = UUID.randomUUID(),
    competition = competitionId,
    competitionTemplate = competitionTemplateId,
    identifier = identifier,
    name = name,
    shortName = shortName,
    description = description,
    countMales = countMales,
    countFemales = countFemales,
    countNonBinary = countNonBinary,
    countMixed = countMixed,
    competitionCategory = competitionCategory,
)

fun NamedParticipantForCompetitionRequestDto.toRecord(propertiesId: UUID) = CompetitionPropertiesHasNamedParticipantRecord(
    competitionProperties = propertiesId,
    namedParticipant = namedParticipant,
    required = required,
    countMales = countMales,
    countFemales = countFemales,
    countNonBinary = countNonBinary,
    countMixed = countMixed
)

fun NamedParticipantForCompetitionPropertiesRecord.toDto(): App<Nothing, NamedParticipantForCompetitionDto> = KIO.ok(
    NamedParticipantForCompetitionDto(
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

fun NamedParticipantForCompetitionPropertiesRecord.applyCompetitionPropertiesHasNamedParticipant(competitionPropertiesId: UUID, namedParticipantId: UUID): App<Nothing, CompetitionPropertiesHasNamedParticipantRecord> = KIO.ok(
    CompetitionPropertiesHasNamedParticipantRecord(
        competitionProperties = competitionPropertiesId,
        namedParticipant = namedParticipantId,
        required = required!!,
        countMales = countMales!!,
        countFemales = countFemales!!,
        countNonBinary = countNonBinary!!,
        countMixed = countMixed!!
    )
)

fun CompetitionPropertiesRequestDto.toUpdateFunction(): CompetitionPropertiesRecord.() -> Unit = let {
    {
        identifier = it.identifier
        name = it.name
        shortName = it.shortName
        description = it.description
        countMales = it.countMales
        countFemales = it.countFemales
        countNonBinary = it.countNonBinary
        countMixed = it.countMixed
        competitionCategory = it.competitionCategory
    }
}