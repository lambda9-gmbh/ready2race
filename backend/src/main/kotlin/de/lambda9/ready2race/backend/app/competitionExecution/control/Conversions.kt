package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionRoundDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundWithMatchesRecord
import de.lambda9.tailwind.core.KIO

fun CompetitionSetupRoundWithMatchesRecord.toCompetitionRoundDto() = KIO.ok(
    CompetitionRoundDto(
        name = setupRoundName!!,
        matches = setupMatches!!.map { setupMatch -> setupMatch!! to matches!!.find { match -> match!!.competitionSetupMatch == setupMatch.id } }
            .map { match ->
                CompetitionMatchDto(
                    name = match.first.name,
                    teams = if (match.second == null) {
                        emptyList()
                    } else {
                        match.second!!.teams!!.map { team ->
                            CompetitionMatchTeamDto(
                                registrationId = team!!.competitionRegistration!!,
                                teamNumber = team.teamNumber!!, // This should not be null because competition_match_teams are not created if the registration teamNumber is missing
                                clubId = team.clubId!!,
                                clubName = team.clubName!!,
                                name = team.registrationName,
                                startNumber = team.startNumber!!,
                                place = team.place
                            )
                        }
                    },
                    weighting = match.first.weighting,
                    executionOrder = match.first.executionOrder,
                    startTime = match.second?.startTime,
                    startTimeOffset = match.first.startTimeOffset,
                )
            },
        required = required!!
    )
)