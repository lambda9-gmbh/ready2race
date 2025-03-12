package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupMatchDto
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupRoundDto
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

fun CompetitionSetupRoundRecord.toDto(matches: List<CompetitionSetupMatchDto>) = CompetitionSetupRoundDto(
    name = name,
    required = required,
    matches = matches,
)

fun CompetitionSetupMatchDto.toRecord(competitionSetupRoundId: UUID) = CompetitionSetupMatchRecord(
    id = UUID.randomUUID(),
    competitionSetupRound = competitionSetupRoundId,
    weighting = weighting,
    teams = teams,
    name = name,
)

fun CompetitionSetupMatchRecord.toDto(outcomes: List<Int>) = CompetitionSetupMatchDto(
    weighting = weighting,
    teams = teams,
    name = name,
    outcomes = outcomes,
)