package de.lambda9.ready2race.backend.app.results.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender

data class EventResultData(
    val name: String,
    val competitions: List<CompetitionResultData>,
) {

    data class CompetitionResultData(
        val identifier: String,
        val name: String,
        val shortName: String?,
        val teams: List<TeamResultData>,
    )

    data class TeamResultData(
        val place: Int,
        val clubName: String,
        val teamName: String,
        val ratingCategory: String,
        val participants: List<ParticipantResultData>,
        val substitutions: List<SubstitutionResultData>
    )

    data class ParticipantResultData(
        val role: String,
        val firstname: String,
        val lastname: String,
        val year: Int,
        val gender: Gender,
        val externalClubName: String?,
    )

    sealed interface SubstitutionResultData {

        data class RoleSwap(
            val left: SubstitutionResultData,
            val right: SubstitutionResultData,
            val round: String,
        ) : SubstitutionResultData

        data class ParticipantSwap(
            val subIn: ParticipantResultData,
            val subOut: ParticipantResultData,
            val round: String,
        ) : SubstitutionResultData

    }
}
