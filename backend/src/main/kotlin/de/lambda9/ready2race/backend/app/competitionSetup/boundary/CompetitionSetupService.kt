package de.lambda9.ready2race.backend.app.competitionSetup.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionProperties.control.CompetitionPropertiesRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.competitionSetup.entity.*
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.kio.onTrueFail
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
    ): App<ServiceError, UUID> = KIO.comprehension {

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
    ): App<ServiceError, Unit> = KIO.comprehension {

        val setupKey = competitionPropertiesId ?: competitionSetupTemplateId!! // There has to be one of the two

        // Cannot edit the setup for a challenge event since the setup structure is defined by the system
        if (competitionPropertiesId != null) {
            val eventId =
                !CompetitionPropertiesRepo.getEventIdByCompetitionPropertiesId(competitionPropertiesId).orDie()
                    .onNullFail { EventError.NotFound }
            !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionSetupError.IsChallengeEvent }
        }

        // Determine which existing rounds have already been created during execution. Those rounds are locked:
        // they may neither be deleted nor changed - only following (not yet created) rounds may be edited.
        val existingRounds = !CompetitionSetupRoundRepo.getBySetupId(setupKey).orDie()
        val existingMatches = !CompetitionSetupMatchRepo.get(existingRounds.map { it.id }).orDie()
        val createdSetupMatchIds = !CompetitionMatchRepo.getExistingSetupMatchIds(existingMatches.map { it.id }).orDie()
        val createdRoundIds = existingMatches
            .filter { createdSetupMatchIds.contains(it.id) }
            .map { it.competitionSetupRound }
            .toSet()

        // Created rounds in execution order (first executed -> last)
        val createdRoundsInOrder = sortRoundRecords(existingRounds).filter { createdRoundIds.contains(it.id) }

        // Validate that the request leaves the created rounds untouched:
        // none of them may be deleted, and they must stay the leading block in their original order
        // (no reordering, no round inserted before or between them).
        val requestRoundIds = requestRounds.mapNotNull { it.id }
        if (!requestRoundIds.containsAll(createdRoundIds)) {
            return@comprehension KIO.fail(CompetitionSetupError.CreatedRoundDeleted)
        }
        val createdIdsInOrder = createdRoundsInOrder.map { it.id }
        val leadingRequestIds = requestRounds.take(createdIdsInOrder.size).map { it.id }
        if (leadingRequestIds != createdIdsInOrder) {
            return@comprehension KIO.fail(CompetitionSetupError.CreatedRoundOrderChanged)
        }

        // Resolve the final id of every requested round: created rounds keep their id, every other round is
        // (re)created with a fresh id (their old records - if any - are deleted below).
        val finalRoundIds = requestRounds.map { round ->
            round.id?.takeIf { createdRoundIds.contains(it) } ?: UUID.randomUUID()
        }

        // Break all next_round pointers first so deletions and re-insertions don't violate the self FK,
        // then delete every round that is not a locked/created round (they are replaced below).
        !CompetitionSetupRoundRepo.clearNextRounds(setupKey).orDie()
        val roundIdsToDelete = existingRounds.map { it.id }.filter { !createdRoundIds.contains(it) }
        if (roundIdsToDelete.isNotEmpty()) {
            !CompetitionSetupRoundRepo.deleteByIds(roundIdsToDelete).orDie()
        }

        data class Batches(
            // Each round keeps its index in the request so rounds can be inserted in a FK-safe order (next_round).
            val rounds: MutableList<Pair<Int, CompetitionSetupRoundRecord>> = mutableListOf(),
            val groups: MutableList<CompetitionSetupGroupRecord> = mutableListOf(),
            val statisticEvaluations: MutableList<CompetitionSetupGroupStatisticEvaluationRecord> = mutableListOf(),
            val matches: MutableList<CompetitionSetupMatchRecord> = mutableListOf(),
            val participants: MutableList<CompetitionSetupParticipantRecord> = mutableListOf(),
            val places: MutableList<CompetitionSetupPlaceRecord> = mutableListOf(),
            val matchNamings: MutableList<CompetitionSetupMatchNamingRecord> = mutableListOf(),
        )

        val records = Batches()

        requestRounds.forEachIndexed { index, round ->
            // Created rounds and their children are kept exactly as they are in the database.
            if (round.id != null && createdRoundIds.contains(round.id)) return@forEachIndexed

            val nextRoundId = finalRoundIds.getOrNull(index + 1)
            val roundRecord =
                round.toRecord(competitionPropertiesId, competitionSetupTemplateId, nextRoundId, finalRoundIds[index])
            records.rounds.add(index to roundRecord)

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

            // Per-participant-count name / execution-order overrides (only deviations from the defaults above)
            round.matchNamings?.forEach { naming ->
                records.matchNamings.add(naming.toRecord(roundRecord.id))
            }
        }

        // Insert the (re)created rounds last-to-first so each next_round target already exists (self FK).
        // Created rounds are never built here, and no new round ever precedes a created round, so a new round's
        // next_round only ever points to another new round (inserted earlier) or to null.
        !CompetitionSetupRoundRepo.create(records.rounds.sortedByDescending { it.first }.map { it.second }).orDie()
        if (records.groups.isNotEmpty()) {
            !CompetitionSetupGroupRepo.create(records.groups).orDie()
        }
        if (records.statisticEvaluations.isNotEmpty()) {
            !CompetitionSetupGroupStatisticEvaluationRepo.create(records.statisticEvaluations).orDie()
        }
        !CompetitionSetupMatchRepo.create(records.matches).orDie()
        !CompetitionSetupParticipantRepo.create(records.participants).orDie()
        !CompetitionSetupPlaceRepo.create(records.places).orDie()
        if (records.matchNamings.isNotEmpty()) {
            !CompetitionSetupMatchNamingRepo.create(records.matchNamings).orDie()
        }

        // Re-link the kept/created rounds to their (possibly new) following round. Their next_round pointers were
        // cleared above, so they are re-set unconditionally based on the requested order.
        createdRoundsInOrder.forEach { created ->
            val index = requestRounds.indexOfFirst { it.id == created.id }
            !CompetitionSetupRoundRepo.updateNextRound(created.id, finalRoundIds.getOrNull(index + 1)).orDie()
        }

        unit
    }

    // Sorts setup rounds into execution order (first executed -> last) by following the next_round chain.
    private fun sortRoundRecords(rounds: List<CompetitionSetupRoundRecord>): List<CompetitionSetupRoundRecord> {
        val sorted = mutableListOf<CompetitionSetupRoundRecord>()
        fun addRoundToSortedList(r: CompetitionSetupRoundRecord?) {
            if (r != null) {
                sorted.add(0, r)
                addRoundToSortedList(rounds.firstOrNull { it.nextRound == r.id })
            }
        }
        addRoundToSortedList(rounds.firstOrNull { it.nextRound == null })
        return sorted
    }

    fun updateCompetitionSetup(
        request: CompetitionSetupDto,
        userId: UUID,
        key: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
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

        val matchNamingRecords = !CompetitionSetupMatchNamingRepo.get(roundRecords.map { it.id }).orDie()

        // Rounds that already have created matches during execution are locked and must not be changed by the client.
        val createdSetupMatchIds = !CompetitionMatchRepo.getExistingSetupMatchIds(matchRecords.map { it.id }).orDie()
        val createdRoundIds = matchRecords
            .filter { createdSetupMatchIds.contains(it.id) }
            .map { it.competitionSetupRound }
            .toSet()

        val roundDtos = sortRoundRecords(roundRecords).map { round ->
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
                },
                matchNamings = matchNamingRecords
                    .filter { naming -> naming.competitionSetupRound == round.id }
                    .map { it.toDto() },
                updatable = !createdRoundIds.contains(round.id),
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