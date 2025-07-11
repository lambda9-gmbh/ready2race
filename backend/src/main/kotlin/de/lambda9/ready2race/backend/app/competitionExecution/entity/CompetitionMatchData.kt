package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistViewRecord
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
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
                teams = teams.sortedBy { it.startNumber }
            )
        }

        private fun StartlistTeamRecord.toData(): App<Nothing, CompetitionMatchTeam> = KIO.comprehension {

            val orderedSubs = substitutions!!.sortedBy { it!!.orderForRound }

            val (addParticipants, removeParticipants) = orderedSubs.fold(mutableMapOf<UUID, String>() to mutableListOf<UUID>()) { acc, s ->
                acc.apply {
                    first.remove(s!!.participantOut!!)
                    if (participants!!.none {it!!.participantId == s.participantIn}) {
                        first.put(s.participantIn!!, s.role!!)
                    }
                    second.remove(s.participantIn!!)
                    if (participants!!.any {it!!.participantId == s.participantOut}) {
                        second.add(s.participantOut!!)
                    }
                }
            }

            val remainingParticipants = participants!!.filter { p ->
                removeParticipants.none { it == p!!.participantId }
            }.map { p ->
                CompetitionMatchParticipant(
                    role = p!!.role!!,
                    firstname = p.firstname!!,
                    lastname = p.lastname!!,
                    year = p.year!!,
                    gender = p.gender!!,
                    externalClubName = p.externalClubName,
                )
            }

            val newParticipants = !addParticipants.toList().traverse { (id, role) ->
                ParticipantRepo.get(id).orDie().onNullDie("foreign key constraint").map { p ->
                    CompetitionMatchParticipant(
                        role = role,
                        firstname = p.firstname,
                        lastname = p.lastname,
                        year = p.year,
                        gender = p.gender,
                        externalClubName = p.externalClubName,
                    )
                }
            }

            val result = remainingParticipants + newParticipants

            KIO.ok(
                CompetitionMatchTeam(
                    startNumber = startNumber!!,
                    clubName = clubName!!,
                    teamName = teamName,
                    participants = result,
                )
            )
        }
    }
}
