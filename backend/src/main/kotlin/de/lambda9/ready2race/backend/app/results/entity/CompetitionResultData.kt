package de.lambda9.ready2race.backend.app.results.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord

data class EventResultData(
    val name: String,
    val competitions: List<CompetitionResultData>,
    val eventDays: List<EventDayRecord>,
) {

    data class CompetitionResultData(
        val identifier: String,
        val name: String,
        val shortName: String?,
        val days: List<EventDayRecord>,
        val teams: List<TeamResultData>,
    )

    data class TeamResultData(
        val place: Int,
        val clubName: String,
        val teamName: String?,
        val participatingClubName: String?,
        val ratingCategory: String?,
        val participants: List<ParticipantResultData>,
        val sortedSubstitutions: List<SubstitutionResultData>
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
            val left: ParticipantResultData,
            val right: ParticipantResultData,
            val round: String,
        ) : SubstitutionResultData

        data class ParticipantSwap(
            val subIn: ParticipantResultData,
            val subOut: ParticipantResultData,
            val round: String,
        ) : SubstitutionResultData

    }
}
