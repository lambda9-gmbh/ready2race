package de.lambda9.ready2race.backend.app.competitionSetup.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupDto
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object CompetitionSetupService {
    fun createCompetitionSetup(
        userId: UUID,
        competitionPropertiesId: UUID,
    ): App<Nothing, UUID> = KIO.comprehension {
        CompetitionSetupRepo.create(LocalDateTime.now().let { now ->
            CompetitionSetupRecord(
                competitionProperties = competitionPropertiesId,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )
        }).orDie()
    }

    fun updateCompetitionSetup(
        request: CompetitionSetupDto,
        userId: UUID,
        key: UUID,
    ): App<CompetitionSetupError, ApiResponse.NoData> = KIO.comprehension {
        val competitionPropertiesId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(key).orDie()
            .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }

        !CompetitionSetupRepo.update(competitionPropertiesId) {
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie().onNullFail { CompetitionSetupError.NotFound }


        !CompetitionSetupRoundRepo.delete(competitionPropertiesId).orDie()

        data class Batches(
            val rounds: MutableList<CompetitionSetupRoundRecord> = mutableListOf(),
            val groups: MutableList<CompetitionSetupGroupRecord> = mutableListOf(),
            val statisticEvaluations: MutableList<CompetitionSetupGroupStatisticEvaluationRecord> = mutableListOf(),
            val matches: MutableList<CompetitionSetupMatchRecord> = mutableListOf(),
            val participants: MutableList<CompetitionSetupParticipantRecord> = mutableListOf()
        )

        val records = Batches()

        request.rounds.reversed().forEach { round ->
            val next = records.rounds.lastOrNull()
            val roundRecord = round.toRecord(competitionPropertiesId, next?.id)
            records.rounds.add(roundRecord)

            fun addParticipants(participants: List<Int>, matchId: UUID?, groupId: UUID?) {
                participants.mapIndexed { index, seed ->
                    val participantRecord = CompetitionSetupParticipantRecord(
                        id = UUID.randomUUID(),
                        competitionSetupMatch = matchId,
                        competitionSetupGroup = groupId,
                        seed = seed,
                        ranking = index + 1
                    )
                    records.participants.add(participantRecord)
                }
            }

            if (!round.matches.isNullOrEmpty()) {
                round.matches.forEachIndexed { index, match ->
                    val matchRecord = match.toRecord(index, roundRecord.id, null)
                    records.matches.add(matchRecord)

                    addParticipants(match.participants, matchRecord.id, null)
                }
            } else if (!round.groups.isNullOrEmpty()) {
                round.groups.forEachIndexed { index, group ->
                    val groupRecord = group.toRecord(index)
                    records.groups.add(groupRecord)

                    group.matches.forEach { match ->
                        val matchRecord = match.toRecord(index, roundRecord.id, groupRecord.id)
                        records.matches.add(matchRecord)
                    }

                    addParticipants(group.participants, null, groupRecord.id)
                }
            }

            round.statisticEvaluations?.forEach { statisticEvaluation ->
                val statisticEvaluationRecord = statisticEvaluation.toRecord(roundRecord.id)
                records.statisticEvaluations.add(statisticEvaluationRecord)
            }
        }

        !CompetitionSetupRoundRepo.create(records.rounds).orDie()
        if (records.groups.isNotEmpty()) {
            !CompetitionSetupGroupRepo.create(records.groups).orDie()
        }
        if (records.statisticEvaluations.isNotEmpty()) {
            !CompetitionSetupGroupStatisticEvaluationRepo.create(records.statisticEvaluations).orDie()
        }
        !CompetitionSetupMatchRepo.create(records.matches).orDie()
        !CompetitionSetupParticipantRepo.create(records.participants).orDie()

        noData
    }

    fun getCompetitionSetup(
        key: UUID,
    ): App<CompetitionSetupError, ApiResponse.Dto<CompetitionSetupDto>> = KIO.comprehension {
        val competitionPropertiesId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(key).orDie()
            .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }


        // Get ALL Records for this Setup

        val roundRecords = !CompetitionSetupRoundRepo.get(competitionPropertiesId).orDie()

        val matchRecords = !CompetitionSetupMatchRepo.get(roundRecords.map { it.id }).orDie()

        val groupRecords = !CompetitionSetupGroupRepo
            .get(matchRecords.mapNotNull { it.competitionSetupGroup })
            .orDie()

        val statisticEvaluationRecords = !CompetitionSetupGroupStatisticEvaluationRepo
            .get(roundRecords.map { it.id })
            .orDie()

        val participantRecords =
            !CompetitionSetupParticipantRepo.get(matchRecords.map { it.id } + groupRecords.map { it.id }).orDie()


        // Map and filter the Records into the Dto
        val rounds: MutableList<CompetitionSetupRoundRecord> = mutableListOf()
        fun addRoundToSortedList(r: CompetitionSetupRoundRecord?) {
            if (r != null) {
                rounds.add(r)

                addRoundToSortedList(roundRecords.firstOrNull { it.nextRound == r.id })
            }
        }
        addRoundToSortedList(roundRecords.firstOrNull { it.nextRound == null })


        val roundDtos = roundRecords.reversed().map { round ->
            // If there are Groups in this round (every match has a group reference): round.matches = null
            // In that case the Matches are assigned to the respective group

            val matchesInRound = matchRecords.filter { match -> match.competitionSetupRound == round.id }

            val roundHasGroups = matchesInRound.getOrNull(0)?.competitionSetupGroup != null

            round.toDto(
                matches = if (!roundHasGroups) {
                    matchesInRound.map { match ->
                        match.toDto(participantRecords.filter { participant -> participant.competitionSetupMatch == match.id }
                            .sortedBy { it.ranking }
                            .map { it.seed })
                    }
                } else {
                    null
                },
                groups = if (roundHasGroups) {
                    // Filter Groups for the Round (Information is stored in the Matches)
                    groupRecords.filter { group ->
                        matchesInRound.map { it.competitionSetupGroup }.contains(group.id)
                    }.map { group ->
                        group.toDto(
                            matches = matchesInRound.filter { it.competitionSetupGroup == group.id }
                                .map { match ->
                                    match.toDto(participantRecords.filter { participant -> participant.competitionSetupMatch == match.id }
                                        .sortedBy { it.ranking }
                                        .map { it.seed })
                                },

                            participants = participantRecords.filter { participant -> participant.competitionSetupGroup == group.id }
                                .sortedBy { it.ranking }
                                .map { it.seed }
                        )
                    }
                } else {
                    null
                },
                // If there are no groups, it can be assumed that there are also no statisticEvaluations for this round
                statisticEvaluations = if (roundHasGroups) {
                    statisticEvaluationRecords.filter { statisticEvaluation ->
                        statisticEvaluation.competitionSetupRound == round.id
                    }.map { it.toDto() }
                } else {
                    null
                },
            )
        }


        KIO.ok(
            ApiResponse.Dto(CompetitionSetupDto(roundDtos))
        )
    }
}