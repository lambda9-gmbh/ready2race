package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupGroupDto
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupGroupStatisticEvaluationDto
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupMatchDto
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupRoundDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupGroupRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupGroupStatisticEvaluationRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import java.util.*

fun CompetitionSetupRoundDto.toRecord(competitionSetupId: UUID, nextRoundId: UUID?) = CompetitionSetupRoundRecord(
    id = UUID.randomUUID(),
    competitionSetup = competitionSetupId,
    nextRound = nextRoundId,
    name = name,
    required = required,
)

fun CompetitionSetupRoundRecord.toDto(
    matches: List<CompetitionSetupMatchDto>?,
    groups: List<CompetitionSetupGroupDto>?,
    statisticEvaluations: List<CompetitionSetupGroupStatisticEvaluationDto>?
) = CompetitionSetupRoundDto(
    name = name,
    required = required,
    matches = matches,
    groups = groups,
    statisticEvaluations = statisticEvaluations
)

fun CompetitionSetupGroupDto.toRecord() = CompetitionSetupGroupRecord(
    id = UUID.randomUUID(),
    duplicatable = duplicatable,
    weighting = weighting,
    teams = teams,
    name = name,
)

fun CompetitionSetupGroupRecord.toDto(matches: List<CompetitionSetupMatchDto>, outcomes: List<Int>) =
    CompetitionSetupGroupDto(
        duplicatable = duplicatable,
        weighting = weighting,
        teams = teams,
        name = name,
        matches = matches,
        outcomes = outcomes
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
        duplicatable = duplicatable,
        weighting = weighting,
        teams = teams,
        name = name,
    )

fun CompetitionSetupMatchRecord.toDto(outcomes: List<Int>?) = CompetitionSetupMatchDto(
    duplicatable = duplicatable,
    weighting = weighting,
    teams = teams,
    name = name,
    outcomes = outcomes,
)