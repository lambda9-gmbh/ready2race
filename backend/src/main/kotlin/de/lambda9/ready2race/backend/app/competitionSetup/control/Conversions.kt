package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.app.competitionSetup.entity.*
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import java.util.*

fun CompetitionSetupRoundDto.toRecord(competitionSetupId: UUID, nextRoundId: UUID?) = CompetitionSetupRoundRecord(
    id = UUID.randomUUID(),
    competitionSetup = competitionSetupId,
    nextRound = nextRoundId,
    name = name,
    required = required,
    useDefaultSeeding = useDefaultSeeding
)

fun CompetitionSetupRoundRecord.toDto(
    matches: List<CompetitionSetupMatchDto>?,
    groups: List<CompetitionSetupGroupDto>?,
    statisticEvaluations: List<CompetitionSetupGroupStatisticEvaluationDto>?,
    places: List<CompetitionSetupPlaceDto>
) = CompetitionSetupRoundDto(
    name = name,
    required = required,
    matches = matches,
    groups = groups,
    statisticEvaluations = statisticEvaluations,
    useDefaultSeeding = useDefaultSeeding,
    places = places
)

fun CompetitionSetupGroupDto.toRecord(index: Int) = CompetitionSetupGroupRecord(
    id = UUID.randomUUID(),
    duplicatable = duplicatable,
    weighting = weighting,
    teams = teams,
    name = name,
    position = index
)

fun CompetitionSetupGroupRecord.toDto(matches: List<CompetitionSetupMatchDto>, participants: List<Int>) =
    CompetitionSetupGroupDto(
        duplicatable = duplicatable,
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

fun CompetitionSetupMatchDto.toRecord(index: Int, competitionSetupRoundId: UUID, competitionSetupGroupId: UUID?) =
    CompetitionSetupMatchRecord(
        id = UUID.randomUUID(),
        competitionSetupRound = competitionSetupRoundId,
        competitionSetupGroup = competitionSetupGroupId,
        duplicatable = duplicatable,
        weighting = weighting,
        teams = teams,
        name = name,
        position = index,
        startTimeOffset = startTimeOffset,
    )

fun CompetitionSetupMatchRecord.toDto(participants: List<Int>) = CompetitionSetupMatchDto(
    duplicatable = duplicatable,
    weighting = weighting,
    teams = teams,
    name = name,
    participants = participants,
    startTimeOffset = startTimeOffset
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