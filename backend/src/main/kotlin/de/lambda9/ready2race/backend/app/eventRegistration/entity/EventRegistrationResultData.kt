package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationResultViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RegisteredCompetitionTeamRecord
import java.util.UUID

data class EventRegistrationResultData(
    val competitionRegistrations: List<CompetitionRegistrationData>,
) {

    data class CompetitionRegistrationData(
        val identifier: String,
        val name: String,
        val shortName: String?,
        val teams: List<TeamRegistrationData>,
    )

    data class TeamRegistrationData(
        val name: String?,
        val clubId: UUID,
        val clubName: String,
        val ratingCategory: RatingCategoryRegistrationData?,
        val participants: List<ParticipantRegistrationData>,
    )

    data class RatingCategoryRegistrationData(
        val id: UUID,
        val name: String,
        val description: String?,
    )

    data class ParticipantRegistrationData(
        val role: String,
        val firstname: String,
        val lastname: String,
        val year: Int,
        val gender: Gender,
        val externalClubName: String?,
    )

    companion object {

        fun fromPersisted(
            result: EventRegistrationResultViewRecord,
            filterTeams: (RegisteredCompetitionTeamRecord) -> Boolean = { true }
        ): EventRegistrationResultData = EventRegistrationResultData(
            competitionRegistrations = result.competitions!!.map { competition ->
                CompetitionRegistrationData(
                    identifier = competition!!.identifier!!,
                    name = competition.name!!,
                    shortName = competition.shortName,
                    teams = competition.teams!!.filter { filterTeams(it!!) }.map { team ->
                        TeamRegistrationData(
                            name = team!!.teamName,
                            clubId = team.clubId!!,
                            clubName = team.clubName!!,
                            ratingCategory = team.ratingCategory?.let {
                                RatingCategoryRegistrationData(
                                    id = it.id,
                                    name = it.name,
                                    description = it.description,
                                )
                            },
                            participants = team.participants!!.map {
                                ParticipantRegistrationData(
                                    role = it!!.role!!,
                                    firstname = it.firstname!!,
                                    lastname = it.lastname!!,
                                    year = it.year!!,
                                    gender = it.gender!!,
                                    externalClubName = it.externalClubName,
                                )
                            }
                        )
                    }
                )
            }
        )
    }
}
