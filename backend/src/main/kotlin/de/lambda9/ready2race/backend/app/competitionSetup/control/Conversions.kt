package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.app.competitionSetup.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*

fun CompetitionSetupRoundDto.toRecord(
    competitionPropertiesId: UUID?,
    competitionSetupTemplateId: UUID?,
    nextRoundId: UUID?,
    roundId: UUID = UUID.randomUUID(),
) = CompetitionSetupRoundRecord(
    id = roundId,
    competitionSetup = competitionPropertiesId,
    competitionSetupTemplate = competitionSetupTemplateId,
    nextRound = nextRoundId,
    name = name,
    required = required,
    useDefaultSeeding = useDefaultSeeding,
    placesOption = placesOption.name,
    isQualification = isQualification,
)

fun CompetitionSetupRoundRecord.toDto(
    matches: List<CompetitionSetupMatchDto>?,
    groups: List<CompetitionSetupGroupDto>?,
    statisticEvaluations: List<CompetitionSetupGroupStatisticEvaluationDto>?,
    places: List<CompetitionSetupPlaceDto>?,
    matchNamings: List<CompetitionSetupMatchNamingDto>?,
    updatable: Boolean = true,
) = CompetitionSetupRoundDto(
    id = id,
    name = name,
    required = required,
    matches = matches,
    groups = groups,
    statisticEvaluations = statisticEvaluations,
    useDefaultSeeding = useDefaultSeeding,
    placesOption = CompetitionSetupPlacesOption.valueOf(placesOption),
    places = places,
    isQualification = isQualification ?: false,
    matchNamings = matchNamings,
    updatable = updatable,
)

fun CompetitionSetupMatchNamingDto.toRecord(competitionSetupRoundId: UUID) = CompetitionSetupMatchNamingRecord(
    competitionSetupRound = competitionSetupRoundId,
    participantCount = participantCount,
    matchWeighting = matchWeighting,
    name = name,
    executionOrder = executionOrder,
)

fun CompetitionSetupMatchNamingRecord.toDto() = CompetitionSetupMatchNamingDto(
    participantCount = participantCount,
    matchWeighting = matchWeighting,
    name = name,
    executionOrder = executionOrder,
)

fun CompetitionSetupGroupDto.toRecord() = CompetitionSetupGroupRecord(
    id = UUID.randomUUID(),
    weighting = weighting,
    teams = teams,
    name = name,
)

fun CompetitionSetupGroupRecord.toDto(matches: List<CompetitionSetupMatchDto>, participants: List<Int>) =
    CompetitionSetupGroupDto(
        weighting = weighting,
        teams = teams,
        name = name,
        matches = matches,
        participants = participants
    )

fun CompetitionSetupGroupStatisticEvaluationDto.toRecord(round: UUID) = CompetitionSetupGroupStatisticEvaluationRecord(
    competitionSetupRound = round,
    name = name,
    priority = priority,
    rankByBiggest = rankByBiggest,
    ignoreBiggestValues = ignoreBiggestValues,
    ignoreSmallestValues = ignoreSmallestValues,
    asAverage = asAverage,
)

fun CompetitionSetupGroupStatisticEvaluationRecord.toDto() = CompetitionSetupGroupStatisticEvaluationDto(
    name = name,
    priority = priority,
    rankByBiggest = rankByBiggest,
    ignoreBiggestValues = ignoreBiggestValues,
    ignoreSmallestValues = ignoreSmallestValues,
    asAverage = asAverage,
)

fun CompetitionSetupMatchDto.toRecord(competitionSetupRoundId: UUID, competitionSetupGroupId: UUID?) =
    CompetitionSetupMatchRecord(
        id = UUID.randomUUID(),
        competitionSetupRound = competitionSetupRoundId,
        competitionSetupGroup = competitionSetupGroupId,
        weighting = weighting,
        teams = teams,
        name = name,
        executionOrder = executionOrder,
        startTimeOffset = startTimeOffset,
    )

fun CompetitionSetupMatchRecord.toDto(participants: List<Int>) = CompetitionSetupMatchDto(
    weighting = weighting,
    teams = teams,
    name = name,
    participants = participants,
    executionOrder = executionOrder,
    startTimeOffset = startTimeOffset
)

fun CompetitionSetupMatchRecord.applyCompetitionMatch(userId: UUID, startTime: LocalDateTime?) = KIO.ok(
    LocalDateTime.now().let { now ->
        CompetitionMatchRecord(
            competitionSetupMatch = id,
            startTime = startTime,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
    }
)

fun CompetitionSetupPlaceDto.toRecord(competitionSetupRoundId: UUID) = CompetitionSetupPlaceRecord(
    competitionSetupRound = competitionSetupRoundId,
    roundOutcome = roundOutcome,
    place = place,
)

fun CompetitionSetupPlaceRecord.toDto() = CompetitionSetupPlaceDto(
    roundOutcome = roundOutcome,
    place = place,
)