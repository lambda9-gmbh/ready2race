package de.lambda9.ready2race.backend.app.eventRegistration.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRegistrationResultViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RegisteredCompetitionTeamRecord

data class EventRegistrationResultData(
    val competitionRegistrations: List<CompetitionRegistrationData>,
) {

    data class CompetitionRegistrationData(
        val identifier: String,
        val name: String,
        val shortName: String?,
        val clubRegistrations: List<ClubRegistrationData>,
    )

    data class ClubRegistrationData(
        val name: String,
        val teams: List<TeamRegistrationData>
    )

    data class TeamRegistrationData(
        val name: String?,
        val participants: List<ParticipantRegistrationData>,
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
                    clubRegistrations = competition.clubRegistrations!!.map { club ->
                        ClubRegistrationData(
                            name = club!!.name!!,
                            teams = club.teams!!.filter { filterTeams(it!!) }.map {
                                TeamRegistrationData(
                                    name = it!!.teamName,
                                    participants = it.participants!!.map {
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
        )
    }
}
