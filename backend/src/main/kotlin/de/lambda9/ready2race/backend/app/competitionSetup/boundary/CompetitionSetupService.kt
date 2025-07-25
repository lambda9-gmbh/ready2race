package de.lambda9.ready2race.backend.app.competitionSetup.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.competitionSetup.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.LocalDateTime
import java.util.*

object CompetitionSetupService {
    fun createCompetitionSetup(
        userId: UUID,
        competitionPropertiesId: UUID,
        setupTemplateId: UUID?,
        createRounds: Boolean
    ): App<Nothing, UUID> = KIO.comprehension {

        val setupId = !CompetitionSetupRepo.create(LocalDateTime.now().let { now ->
            CompetitionSetupRecord(
                competitionProperties = competitionPropertiesId,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )
        }).orDie()

        if (setupTemplateId != null && createRounds) {
            val templateRounds = !getCompetitionSetupRoundsWithContent(setupTemplateId)
            !updateCompetitionSetupRounds(templateRounds, competitionPropertiesId, null)
        }

        KIO.ok(setupId)
    }

    fun updateCompetitionSetupRounds(
        requestRounds: List<CompetitionSetupRoundDto>,
        competitionPropertiesId: UUID?,
        competitionSetupTemplateId: UUID?
    ): App<Nothing, Unit> = KIO.comprehension {

        // Todo: Check if a round has already been generated for the competition - that round should be locked from being updated

        // Deletes all rounds for this competition - including matches, groups, places etc. by cascade
        !CompetitionSetupRoundRepo.delete(
            competitionPropertiesId ?: competitionSetupTemplateId!! // There has to be one of the two
        ).orDie()

        data class Batches(
            val rounds: MutableList<CompetitionSetupRoundRecord> = mutableListOf(),
            val groups: MutableList<CompetitionSetupGroupRecord> = mutableListOf(),
            val statisticEvaluations: MutableList<CompetitionSetupGroupStatisticEvaluationRecord> = mutableListOf(),
            val matches: MutableList<CompetitionSetupMatchRecord> = mutableListOf(),
            val participants: MutableList<CompetitionSetupParticipantRecord> = mutableListOf(),
            val places: MutableList<CompetitionSetupPlaceRecord> = mutableListOf(),
        )

        val records = Batches()

        requestRounds.reversed().forEach { round ->
            val next = records.rounds.lastOrNull()
            val roundRecord = round.toRecord(competitionPropertiesId, competitionSetupTemplateId, next?.id)
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

            if (round.matches != null) {
                round.matches.forEach { match ->
                    val matchRecord = match.toRecord(roundRecord.id, null)
                    records.matches.add(matchRecord)

                    addParticipants(match.participants, matchRecord.id, null)
                }
            } else if (round.groups != null) {
                round.groups.forEach { group ->
                    val groupRecord = group.toRecord()
                    records.groups.add(groupRecord)

                    group.matches.forEach { match ->
                        val matchRecord = match.toRecord(roundRecord.id, groupRecord.id)
                        records.matches.add(matchRecord)
                    }

                    addParticipants(group.participants, null, groupRecord.id)
                }
            }

            round.statisticEvaluations?.forEach { statisticEvaluation ->
                val statisticEvaluationRecord = statisticEvaluation.toRecord(roundRecord.id)
                records.statisticEvaluations.add(statisticEvaluationRecord)
            }

            // If the option is NOT custom, no places are saved in the database since they can be calculated anytime
            if (round.placesOption == CompetitionSetupPlacesOption.CUSTOM) {
                round.places?.forEach { place ->
                    val placeRecord = place.toRecord(roundRecord.id)
                    records.places.add(placeRecord)
                }
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
        !CompetitionSetupPlaceRepo.create(records.places).orDie()

        unit
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

        !updateCompetitionSetupRounds(request.rounds, competitionPropertiesId, null)

        noData
    }

    fun getCompetitionSetupRoundsWithContent(
        key: UUID,
    ): App<Nothing, List<CompetitionSetupRoundDto>> = KIO.comprehension {
        // There has to be one of the two keys
        val roundRecords = !CompetitionSetupRoundRepo.getBySetupId(key).orDie()

        val matchRecords = !CompetitionSetupMatchRepo.get(roundRecords.map { it.id }).orDie()

        val groupRecords = !CompetitionSetupGroupRepo
            .get(matchRecords.mapNotNull { it.competitionSetupGroup })
            .orDie()

        val statisticEvaluationRecords = !CompetitionSetupGroupStatisticEvaluationRepo
            .get(roundRecords.map { it.id })
            .orDie()

        val participantRecords =
            !CompetitionSetupParticipantRepo.get(matchRecords.map { it.id } + groupRecords.map { it.id }).orDie()


        val placeRecords = !CompetitionSetupPlaceRepo.get(roundRecords.map { it.id }).orDie()


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
                // If placesOption is not custom, no places will be returned
                places = if (CompetitionSetupPlacesOption.valueOf(round.placesOption) == CompetitionSetupPlacesOption.CUSTOM) {
                    placeRecords.filter { place -> place.competitionSetupRound == round.id }.map { it.toDto() }
                } else {
                    null
                }

            )
        }

        KIO.ok(roundDtos)
    }

    fun getCompetitionSetup(
        key: UUID,
    ): App<CompetitionSetupError, ApiResponse.Dto<CompetitionSetupDto>> = KIO.comprehension {
        val competitionPropertiesId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(key).orDie()
            .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }

        val roundDtos = !getCompetitionSetupRoundsWithContent(competitionPropertiesId)

        KIO.ok(
            ApiResponse.Dto(CompetitionSetupDto(roundDtos))
        )
    }

    fun getSetupRoundsWithMatches(
        key: UUID,
    ): App<CompetitionSetupError, List<CompetitionSetupRoundWithMatches>> = KIO.comprehension {
        val setupId = !CompetitionPropertiesRepo.getIdByCompetitionOrTemplateId(key).orDie()
            .onNullFail { CompetitionSetupError.CompetitionPropertiesNotFound }

        val records = !CompetitionSetupRoundRepo.getWithMatchesBySetup(setupId).orDie()

        records.traverse { it.toCompetitionSetupRoundWithMatches() }
    }
}