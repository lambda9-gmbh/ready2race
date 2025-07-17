package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.StartlistViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
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

            val orderedSubs = substitutions!!.filterNotNull().sortedBy { it.orderForRound }

            val participantsStillInToRole = participants!!.filterNotNull().map { p ->
                val subsRelevantForParticipant = orderedSubs.filter { sub ->
                    sub.participantIn!!.id == p.participantId || sub.participantOut!!.id == p.participantId
                }
                p to subsRelevantForParticipant
            }.filter { pToSubs ->
                if (pToSubs.second.isEmpty()) {
                    true
                } else {
                    pToSubs.second.last().participantIn!!.id == pToSubs.first.participantId
                }
            }.map { pToSubs ->
                pToSubs.first to if (pToSubs.second.isEmpty()) {
                    pToSubs.first.role!!
                } else {
                    pToSubs.second.last().namedParticipantName!!
                }
            }

            val subbedInParticipants = orderedSubs
                .filter { sub ->
                    // Filter subIns by participantsStillInToRole
                    if (participantsStillInToRole.none { it.first.participantId == sub.participantIn!!.id }) {
                        val substitutionsRelevantForSubIn = orderedSubs.filter {
                            sub.participantIn!!.id == it.participantOut!!.id || sub.participantIn!!.id == it.participantIn!!.id
                        }
                        if (substitutionsRelevantForSubIn.isNotEmpty()) {
                            // If the last sub was sub.participantIn being subbed in - add it to subbedInParticipants
                            substitutionsRelevantForSubIn.last().participantIn!!.id == sub.participantIn!!.id
                        } else {
                            false
                        }
                    } else false
                }.map {
                    CompetitionMatchParticipant(
                        role = it.namedParticipantName!!,
                        firstname = it.participantIn!!.firstname,
                        lastname = it.participantIn!!.lastname,
                        year = it.participantIn!!.year,
                        gender = it.participantIn!!.gender,
                        externalClubName = it.participantIn!!.externalClubName,
                    )
                }

            val mappedParticipantsStillIn = participantsStillInToRole.map { p ->
                CompetitionMatchParticipant(
                    role = p.second,
                    firstname = p.first.firstname!!,
                    lastname = p.first.lastname!!,
                    year = p.first.year!!,
                    gender = p.first.gender!!,
                    externalClubName = p.first.externalClubName,
                )
            }

            val result = mappedParticipantsStillIn + subbedInParticipants

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
