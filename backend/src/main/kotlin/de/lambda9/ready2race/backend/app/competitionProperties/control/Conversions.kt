package de.lambda9.ready2race.backend.app.competitionProperties.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionProperties.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import java.util.*

fun CompetitionPropertiesRequest.toRecord(competitionId: UUID?, competitionTemplateId: UUID?) =
    CompetitionPropertiesRecord(
        id = UUID.randomUUID(),
        competition = competitionId,
        competitionTemplate = competitionTemplateId,
        identifier = identifier,
        name = name,
        shortName = shortName,
        description = description,
        competitionCategory = competitionCategory,
        lateRegistrationAllowed = lateRegistrationAllowed,
        ratingCategoryRequired = ratingCategoryRequired,
    )

fun NamedParticipantForCompetitionRequestDto.toRecord(propertiesId: UUID) =
    CompetitionPropertiesHasNamedParticipantRecord(
        competitionProperties = propertiesId,
        namedParticipant = namedParticipant,
        countMales = countMales,
        countFemales = countFemales,
        countNonBinary = countNonBinary,
        countMixed = countMixed
    )

fun FeeForCompetitionRequestDto.toRecord(propertiesId: UUID) = CompetitionPropertiesHasFeeRecord(
    id = UUID.randomUUID(),
    competitionProperties = propertiesId,
    fee = fee,
    required = required,
    amount = amount,
    lateAmount = lateAmount,
)

fun NamedParticipantForCompetitionPropertiesRecord.toDto(): App<Nothing, NamedParticipantForCompetitionDto> = KIO.ok(
    NamedParticipantForCompetitionDto(
        id = id!!,
        name = name!!,
        description = description,
        countMales = countMales!!,
        countFemales = countFemales!!,
        countNonBinary = countNonBinary!!,
        countMixed = countMixed!!
    )
)

fun FeeForCompetitionPropertiesRecord.toDto(): App<Nothing, FeeForCompetitionDto> = KIO.ok(
    FeeForCompetitionDto(
        id = id!!,
        name = name!!,
        description = description,
        required = required!!,
        amount = amount!!,
        lateAmount = lateAmount,
    )
)

fun CompetitionPropertiesRequest.toUpdateFunction(): CompetitionPropertiesRecord.() -> Unit = let {
    {
        identifier = it.identifier
        name = it.name
        shortName = it.shortName
        description = it.description
        competitionCategory = it.competitionCategory
        lateRegistrationAllowed = it.lateRegistrationAllowed
        ratingCategoryRequired = it.ratingCategoryRequired
    }
}

fun CompetitionChallengeConfigRequest.toRecord(propertiesId: UUID) = KIO.ok(
    CompetitionPropertiesChallengeConfigRecord(
        competitionProperties = propertiesId,
        resultConfirmationImageRequired = resultConfirmationImageRequired,
        startAt = startAt,
        endAt = endAt,
    )
)