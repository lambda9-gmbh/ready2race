package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchWithTeamsRecord
import java.time.LocalDateTime

data class CompetitionMatchData(
    val matchName: String?,
    val roundName: String,
    val order: Int,
    val startTime: LocalDateTime,
    val startTimeOffset: Long?,
    val competition: CompetitionData,
    val teams: List<CompetitionMatchTeam>,
) {

    data class CompetitionData(
        val identifier: String,
        val name: String,
        val shortName: String?,
        val category: String?,
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

    companion object {

        //TODO: @Incomplete: need substitution changes

        /**
         * This expects startTime to be set and not be null.
         */
        fun fromPersisted(
            persisted: CompetitionMatchWithTeamsRecord
        ): CompetitionMatchData = CompetitionMatchData(
            matchName = null, //missing
            roundName = "ph_round", //missing
            order = 0, // missing
            startTime = persisted.startTime!!,
            startTimeOffset = 60000, // TODO: @Incomplete: properties missing from view
            competition = CompetitionData(
                identifier = "ph",
                name = "placehodler",
                shortName = "[ph]",
                category = null,
            ),
            teams = persisted.teams!!.sortedBy { it!!.startNumber }.map { team ->
                CompetitionMatchTeam(
                    startNumber = team!!.startNumber!!,
                    clubName = team.clubName!!,
                    teamName = team.registrationName,
                    participants = team.participants!!.map { p ->
                        CompetitionMatchParticipant(
                            role = p!!.role!!,
                            firstname = p.firstname!!,
                            lastname = p.lastname!!,
                            year = p.year!!,
                            gender = p.gender!!,
                            externalClubName = p.externalClubName,
                        )
                    }
                )
            }
        )
    }
}
