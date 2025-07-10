package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.time.LocalDateTime

data class CompetitionMatchData(
    val startTime: LocalDateTime,
    val competition: CompetitionData,
    val teams: List<CompetitionMatchTeam>,
) {

    data class CompetitionData(
        val identifier: String,
        val name: String,
        val shortName: String?,
    )

    data class CompetitionMatchTeam(
        val startNumber: Int,
        val clubName: String,
        val teamName: String?,
        val participants: List<CompetitionMatchParticipant>,
    )

    data class CompetitionMatchParticipant(
        val role: String,
        val firstname: String,
        val lastname: String,
        val year: Int,
        val gender: Gender,
        val externalClubName: String?,
    )
}
