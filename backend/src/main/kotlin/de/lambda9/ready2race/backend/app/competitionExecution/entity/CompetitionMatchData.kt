package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.substitution.control.toParticipantForExecutionDto
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistViewRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.UUID
import kotlin.String

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
        val registeringClubName: String,
        val actualClubName: String?,
        val teamName: String?,
        val ratingCategory: CompetitionMatchTeamRatingCategory?,
        val participants: List<CompetitionMatchParticipant>,
    )

    data class CompetitionMatchTeamRatingCategory(
        val id: UUID,
        val name: String,
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

        /**
         * This expects startTime to be set and not be null.
         */
        fun fromPersisted(
            persisted: StartlistViewRecord,
        ): App<Nothing, CompetitionMatchData> = persisted.teams!!.toList().traverse {
            it!!.toData()
        }.map { teams ->
            CompetitionMatchData(
                matchName = persisted.name,
                roundName = persisted.roundName!!,
                order = persisted.executionOrder!!,
                startTime = persisted.startTime!!,
                startTimeOffset = persisted.startTimeOffset,
                competition = CompetitionData(
                    identifier = persisted.competitionIdentifier!!,
                    name = persisted.competitionName!!,
                    shortName = persisted.competitionShortName,
                    category = persisted.competitionCategory,
                ),
                teams = teams
            )
        }

        private fun StartlistTeamRecord.toData(): App<Nothing, CompetitionMatchTeam> = KIO.comprehension {

            val participantsWithData = participants!!.filterNotNull().map{
                !it.toParticipantForExecutionDto(
                    clubId = clubId!!,
                    clubName = clubName!!,
                    registrationId = teamId!!,
                    registrationName = teamName,
                )
            }

            val actuallyParticipatingParticipants = !CompetitionExecutionService.getActuallyParticipatingParticipants(
                teamParticipants = participantsWithData,
                substitutionsForRegistration = substitutions!!.filterNotNull(),
            ).map { list ->
                list.map { p ->
                    CompetitionMatchParticipant(
                        role = p.namedParticipantName,
                        firstname = p.firstName,
                        lastname = p.lastName,
                        year = p.year,
                        gender = p.gender,
                        externalClubName = p.externalClubName,
                    )
                }
            }

            val clubs = actuallyParticipatingParticipants.map { it.externalClubName }.toSet()

            val actualClubName = if (clubs.size == 1) {
                clubs.first()
            } else {
                // TODO: read from configuration
                "Renngemeinschaft"
            }

            KIO.ok(
                CompetitionMatchTeam(
                    startNumber = startNumber!!,
                    registeringClubName = clubName!!,
                    actualClubName = actualClubName,
                    teamName = teamName,
                    participants = actuallyParticipatingParticipants,
                    ratingCategory = ratingCategory?.let {
                        CompetitionMatchTeamRatingCategory(
                            id = it.id,
                            name = it.name,
                        )
                    }
                )
            )
        }
    }
}
