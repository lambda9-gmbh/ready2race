package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM_RESULT
import de.lambda9.ready2race.backend.database.select
import java.util.*

object CompetitionMatchTeamResultRepo {

    fun getByCompetitionRegistrationIds(competitionRegistrationIds: List<UUID>) =
        COMPETITION_MATCH_TEAM_RESULT.select { COMPETITION_REGISTRATION.`in`(competitionRegistrationIds) }

}