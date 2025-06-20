package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionRoundDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamDto
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundWithMatchesRecord
import de.lambda9.tailwind.core.KIO

fun CompetitionSetupRoundWithMatchesRecord.toCompetitionRoundDto() = KIO.ok(
    CompetitionRoundDto(
        name = setupRoundName!!,
        matches = matches!!.map { match -> match!! to setupMatches!!.find { setupMatch -> setupMatch!!.id == match.competitionSetupMatch }!! }
            .map { match ->
                CompetitionMatchDto(
                    id = match.second.id,
                    name = match.second.name,
                    teams = match.first.teams!!.map { team ->
                        CompetitionMatchTeamDto(
                            registrationId = team!!.competitionRegistration!!,
                            teamNumber = team.teamNumber!!, // This should not be null because competition_match_teams are not created if the registration teamNumber is missing
                            clubId = team.clubId!!,
                            clubName = team.clubName!!,
                            name = team.registrationName,
                            startNumber = team.startNumber!!,
                            place = team.place
                        )
                    },
                    weighting = match.second.weighting,
                    executionOrder = match.second.executionOrder,
                    startTime = match.first.startTime,
                    startTimeOffset = match.second.startTimeOffset,
                )
            },
        required = required!!
    )
)