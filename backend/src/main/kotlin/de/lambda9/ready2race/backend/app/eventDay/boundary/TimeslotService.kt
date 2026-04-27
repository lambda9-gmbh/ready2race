package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.sortRounds
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventDay.entity.*
import de.lambda9.ready2race.backend.app.eventDay.control.TimeslotRepo
import de.lambda9.ready2race.backend.app.eventDay.control.toDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotRecord
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

object TimeslotService {

    private val descriptionTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private data class OccurringRoundMatches(
        val roundId: UUID,
        val roundName: String,
        val matches: List<OccurringMatch>,
    )

    private data class OccurringMatch(
        val matchId: UUID,
        val matchName: String,
        val occurringTeamCount: Int,
        val startTimeOffsetSeconds: Long?,
    )

    private data class TimedRoundMatches(
        val roundName: String,
        val matches: List<TimedMatch>,
    )

    private data class TimedMatch(
        val matchName: String,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    private data class RecalculatedTimeslotData(
        val endTime: LocalTime,
        val autoDescription: String?,
    )

    private fun String?.toMeaningfulDescription(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.normalizeForComparison(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun calculateOffsetDurationMinutesRoundedUp(
        occurringTeamCount: Int,
        startTimeOffsetRaw: Long?,
    ): Long {
        val normalizedOffsetSeconds = when {
            startTimeOffsetRaw == null || startTimeOffsetRaw <= 0L -> 0L
            startTimeOffsetRaw >= 1000L && startTimeOffsetRaw % 1000L == 0L -> startTimeOffsetRaw / 1000L
            else -> startTimeOffsetRaw
        }
        val nonNegativeStartTimeOffsetSeconds = maxOf(0L, normalizedOffsetSeconds)
        if (nonNegativeStartTimeOffsetSeconds <= 0L) {
            return 0L
        }

        val additionalStartShifts = maxOf(0, occurringTeamCount - 1)
        if (additionalStartShifts <= 0) {
            return 0L
        }

        val totalOffsetSeconds = additionalStartShifts.toLong() * nonNegativeStartTimeOffsetSeconds
        return (totalOffsetSeconds + 59L) / 60L
    }

    private fun composeDescription(
        manualDescription: String?,
        autoDescription: String?,
    ): String? {
        val meaningfulManualDescription = manualDescription.toMeaningfulDescription()
        val meaningfulAutoDescription = autoDescription.toMeaningfulDescription()

        return when {
            meaningfulManualDescription != null && meaningfulAutoDescription != null ->
                "$meaningfulManualDescription\n\n$meaningfulAutoDescription"

            meaningfulManualDescription != null -> meaningfulManualDescription
            meaningfulAutoDescription != null -> meaningfulAutoDescription
            else -> null
        }
    }

    private fun stripAutoPartFromManualDescription(
        manualDescription: String?,
        autoDescription: String?,
    ): String? {
        val meaningfulManualDescription = manualDescription.toMeaningfulDescription() ?: return null
        val meaningfulAutoDescription = autoDescription.toMeaningfulDescription() ?: return meaningfulManualDescription

        if (meaningfulManualDescription == meaningfulAutoDescription) {
            return null
        }

        val autoSuffix = "\n\n$meaningfulAutoDescription"
        return if (meaningfulManualDescription.endsWith(autoSuffix)) {
            meaningfulManualDescription.removeSuffix(autoSuffix).toMeaningfulDescription()
        } else {
            meaningfulManualDescription
        }
    }

    private fun hasReference(
        competitionReference: UUID?,
        roundReference: UUID?,
        matchReference: UUID?,
    ): Boolean =
        competitionReference != null || roundReference != null || matchReference != null

    private fun resolveManualDescriptionForUpdate(
        timeslotId: UUID,
        requestedDescription: String?,
    ): App<ServiceError, String?> = KIO.comprehension {
        val storedDescriptionParts = !TimeslotRepo.getDescriptionParts(timeslotId).orDie()
        val storedAutoDescription = storedDescriptionParts?.auto.toMeaningfulDescription()

        val requestedManualDescription = requestedDescription.toMeaningfulDescription()
        val cleanedRequestedManualDescription =
            stripAutoPartFromManualDescription(requestedManualDescription, storedAutoDescription)

        val storedManualDescription = stripAutoPartFromManualDescription(
            manualDescription = storedDescriptionParts?.manual.toMeaningfulDescription()
                ?: storedDescriptionParts?.description.toMeaningfulDescription(),
            autoDescription = storedAutoDescription,
        )
        val storedComposedDescription = composeDescription(storedManualDescription, storedAutoDescription)

        val shouldKeepStoredManualDescription =
            requestedManualDescription.normalizeForComparison() == storedComposedDescription.normalizeForComparison()

        KIO.ok(
            if (shouldKeepStoredManualDescription) {
                storedManualDescription
            } else {
                cleanedRequestedManualDescription
            }
        )
    }

    private fun calculateRecalculatedTimeslotData(
        competitionReference: UUID,
        roundReference: UUID?,
        matchReference: UUID?,
        startTime: LocalTime,
        matchDuration: Int,
        matchGapDuration: Int,
    ): App<ServiceError, RecalculatedTimeslotData> = KIO.comprehension {
        if (matchDuration <= 0) {
            return@comprehension KIO.ok(
                RecalculatedTimeslotData(
                    endTime = startTime,
                    autoDescription = null
                )
            )
        }

        val nonNegativeMatchGapDuration = maxOf(0, matchGapDuration)
        val matchCountSummary = !EventDayService.countMatchesToFinal(competitionReference)
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionReference)
        val sortedRounds = sortRounds(setupRounds)
        val roundInfoById = matchCountSummary.perRound.associateBy { it.setupRoundId }

        val roundsWithOccurringMatches = sortedRounds.mapNotNull { setupRound ->
            val roundInfo = roundInfoById[setupRound.setupRoundId] ?: return@mapNotNull null
            if (roundInfo.occurringSetupMatchIds.isEmpty()) {
                return@mapNotNull null
            }

            val setupMatchById = setupRound.setupMatches.associateBy { it.id }
            val occurringMatchesInRound = roundInfo.occurringSetupMatchIds
                .mapNotNull { setupMatchById[it] }
                .map { setupMatch ->
                    OccurringMatch(
                        matchId = setupMatch.id,
                        matchName = setupMatch.name ?: "",
                        occurringTeamCount = roundInfo.occurringTeamCountBySetupMatchId[setupMatch.id] ?: 0,
                        startTimeOffsetSeconds = setupMatch.startTimeOffset,
                    )
                }

            if (occurringMatchesInRound.isEmpty()) {
                null
            } else {
                OccurringRoundMatches(
                    roundId = setupRound.setupRoundId,
                    roundName = setupRound.setupRoundName,
                    matches = occurringMatchesInRound,
                )
            }
        }

        val scopedRoundsWithOccurringMatches = when {
            matchReference != null ->
                roundsWithOccurringMatches.mapNotNull { round ->
                    val matchingOccurringMatch = round.matches.filter { it.matchId == matchReference }
                    if (matchingOccurringMatch.isEmpty()) {
                        null
                    } else {
                        round.copy(matches = matchingOccurringMatch)
                    }
                }

            roundReference != null ->
                roundsWithOccurringMatches.filter { it.roundId == roundReference }

            else -> roundsWithOccurringMatches
        }

        val scopedMatchesInOrder = scopedRoundsWithOccurringMatches.flatMap { round ->
            round.matches.map { occurringMatch -> round.roundId to occurringMatch }
        }

        if (scopedMatchesInOrder.isEmpty()) {
            return@comprehension KIO.ok(
                RecalculatedTimeslotData(
                    endTime = startTime,
                    autoDescription = null,
                )
            )
        }

        var currentMatchStartTime = startTime
        val timedMatchesByRoundId = mutableMapOf<UUID, MutableList<TimedMatch>>()

        scopedMatchesInOrder.forEachIndexed { matchIndex, (roundId, occurringMatch) ->
            val matchStartTime = currentMatchStartTime
            val offsetDurationMinutes = calculateOffsetDurationMinutesRoundedUp(
                occurringTeamCount = occurringMatch.occurringTeamCount,
                startTimeOffsetRaw = occurringMatch.startTimeOffsetSeconds,
            )
            val simulatedMatchDurationMinutes = matchDuration.toLong() + offsetDurationMinutes
            val matchEndTime = matchStartTime.plusMinutes(simulatedMatchDurationMinutes)

            timedMatchesByRoundId.getOrPut(roundId) { mutableListOf() }
                .add(
                    TimedMatch(
                        matchName = occurringMatch.matchName,
                        startTime = matchStartTime,
                        endTime = matchEndTime,
                    )
                )

            currentMatchStartTime = if (matchIndex < scopedMatchesInOrder.lastIndex) {
                matchEndTime.plusMinutes(nonNegativeMatchGapDuration.toLong())
            } else {
                matchEndTime
            }
        }

        val timedRounds = scopedRoundsWithOccurringMatches.mapNotNull { round ->
            val timedMatches = timedMatchesByRoundId[round.roundId]
            if (timedMatches.isNullOrEmpty()) {
                null
            } else {
                TimedRoundMatches(
                    roundName = round.roundName,
                    matches = timedMatches,
                )
            }
        }

        val roundIndentation = "    "
        val matchIndentation = "        "

        val autoDescription = timedRounds.joinToString("\n\n") { round ->
            val roundStartTime = round.matches.first().startTime.format(descriptionTimeFormatter)
            val roundEndTime = round.matches.last().endTime.format(descriptionTimeFormatter)
            val roundNameLine = "$roundIndentation${round.roundName}".trimEnd()
            val roundTimeLine = "$roundIndentation$roundStartTime - $roundEndTime".trimEnd()

            val matchBlocks = round.matches.joinToString("\n") { match ->
                val matchStartTime = match.startTime.format(descriptionTimeFormatter)
                val matchEndTime = match.endTime.format(descriptionTimeFormatter)
                val matchNameLine = "$matchIndentation${match.matchName}".trimEnd()
                val matchTimeLine = "$matchIndentation$matchStartTime - $matchEndTime".trimEnd()
                "$matchNameLine\n$matchTimeLine"
            }

            if (matchBlocks.isNotEmpty()) {
                "$roundNameLine\n$roundTimeLine\n$matchBlocks"
            } else {
                "$roundNameLine\n$roundTimeLine"
            }
        }.toMeaningfulDescription()

        KIO.ok(
            RecalculatedTimeslotData(
                endTime = currentMatchStartTime,
                autoDescription = autoDescription,
            )
        )
    }

    private fun recalculateReferencedTimeslotData(
        timeslotId: UUID,
        userId: UUID,
        manualDescriptionOverride: String? = null,
    ): App<ServiceError, Unit> = KIO.comprehension {
        val timeslot = !TimeslotRepo.getTimeslot(timeslotId).orDie()
            .onNullFail { EventDayError.TimeslotNotFound }

        if (!hasReference(timeslot.competitionReference, timeslot.roundReference, timeslot.matchReference)) {
            return@comprehension KIO.ok(Unit)
        }

        val storedDescriptionParts = !TimeslotRepo.getDescriptionParts(timeslotId).orDie()
        val rawManualDescription =
            manualDescriptionOverride.toMeaningfulDescription()
                ?: storedDescriptionParts?.manual.toMeaningfulDescription()
                ?: storedDescriptionParts?.description.toMeaningfulDescription()
        val manualDescription = stripAutoPartFromManualDescription(
            manualDescription = rawManualDescription,
            autoDescription = storedDescriptionParts?.auto.toMeaningfulDescription()
        )

        val timeslotWithDuration = !TimeslotRepo.getWithCompetitionDurationByTimeslotId(timeslotId).orDie()
        val timeslotCompetitionReference = timeslot.competitionReference
        val timeslotRoundReference = timeslot.roundReference
        val timeslotMatchReference = timeslot.matchReference
        val startTime = timeslotWithDuration?.startTime
        val matchDuration = timeslotWithDuration?.matchDuration
        val matchGapDuration = timeslotWithDuration?.matchGapsDuration

        val recalculatedTimeslotData =
            if (
                timeslotCompetitionReference != null &&
                startTime != null &&
                matchDuration != null &&
                    matchGapDuration != null
            ) {
                !calculateRecalculatedTimeslotData(
                    competitionReference = timeslotCompetitionReference,
                    roundReference = timeslotRoundReference,
                    matchReference = timeslotMatchReference,
                    startTime = startTime,
                    matchDuration = matchDuration,
                    matchGapDuration = matchGapDuration,
                )
            } else {
                null
            }

        val autoDescription = recalculatedTimeslotData?.autoDescription
        val recalculatedEndTime = recalculatedTimeslotData?.endTime ?: timeslot.endTime

        !TimeslotRepo.update(timeslotId) {
            endTime = recalculatedEndTime
            description = manualDescription
            competitionReference = timeslotCompetitionReference
            roundReference = timeslotRoundReference
            matchReference = timeslotMatchReference
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { EventDayError.TimeslotNotFound }

        !TimeslotRepo.updateDescriptionParts(timeslotId, manualDescription, autoDescription).orDie()

        KIO.ok(Unit)
    }

    fun pageByEventDay(
        eventDayId: UUID,
        params: PaginationParameters<TimeslotSort>,
    ): App<Nothing, ApiResponse.Page<TimeslotDto, TimeslotSort>> = KIO.comprehension {

        val total = !TimeslotRepo.countByEventDay(eventDayId, params.search).orDie()

        val page =
            !TimeslotRepo.pageByEventDay(eventDayId, params).orDie()

        val list = page.map { it.toDto() }
        KIO.ok(ApiResponse.Page(
            data = list,
            pagination = params.toPagination(total)
        ))
    }

    fun getTimeslot(
        timeslotId: UUID
    ): App<ServiceError, ApiResponse> = KIO.comprehension {

        val timeslot = !TimeslotRepo.getTimeslot(timeslotId).orDie()
            .onNullFail { EventDayError.TimeslotNotFound }

        KIO.ok(ApiResponse.Dto(timeslot.toDto()))
    }

    fun addTimeslotToEventDay(
        request: TimeslotRequest,
        userId: UUID,
        eventDayId: UUID
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        if (request.matchReference != null) {
            !TimeslotRepo.timeslotForCompetitionUnitExists(request.matchReference, eventDayId).orDie().onTrueFail {
                EventDayError.CompetitionUnitAlreadyHasTimeslot
            }
            !TimeslotRepo.higherCompetitionUnitTimeslotAlreadyExists(
                eventDayId,
                request.competitionReference,
                request.roundReference
            ).orDie().onTrueFail {
                EventDayError.HigherCompetitionUnitAlreadyHasTimeslot
            }
        } else if (request.roundReference != null) {
            !TimeslotRepo.timeslotForCompetitionUnitExists(request.roundReference, eventDayId).orDie().onTrueFail {
                EventDayError.CompetitionUnitAlreadyHasTimeslot
            }
            !TimeslotRepo.lowerCompetitionUnitTimeslotAlreadyExists(request.roundReference, eventDayId).orDie().onTrueFail {
                EventDayError.LowerCompetitionUnitAlreadyHasTimeslot
            }
            !TimeslotRepo.higherCompetitionUnitTimeslotAlreadyExists(
                eventDayId,
                request.competitionReference,
                null
            ).orDie().onTrueFail {
                EventDayError.HigherCompetitionUnitAlreadyHasTimeslot
            }
        } else if (request.competitionReference != null) {
            !TimeslotRepo.timeslotForCompetitionUnitExists(request.competitionReference, eventDayId).orDie().onTrueFail {
                EventDayError.CompetitionUnitAlreadyHasTimeslot
            }
            !TimeslotRepo.lowerCompetitionUnitTimeslotAlreadyExists(request.competitionReference, eventDayId).orDie().onTrueFail {
                EventDayError.LowerCompetitionUnitAlreadyHasTimeslot
            }
        }



        !EventDayRepo.exists(eventDayId).orDie()
            .onNullFail { EventDayError.EventDayNotFound }

        val manualDescription = request.description.toMeaningfulDescription()

        val record = TimeslotRecord(
            id = UUID.randomUUID(),
            eventDay = eventDayId,
            name = request.name,
            description = composeDescription(manualDescription, null),
            startTime = request.startTime,
            endTime = request.endTime,
            competitionReference = request.competitionReference,
            roundReference = request.roundReference,
            matchReference = request.matchReference,
            createdAt = LocalDateTime.now(),
            createdBy = userId,
            updatedAt = LocalDateTime.now(),
            updatedBy = userId
        )

        val id = !TimeslotRepo.create(record).orDie()
        !TimeslotRepo.updateDescriptionParts(id, manualDescription, null).orDie()

        if (hasReference(request.competitionReference, request.roundReference, request.matchReference)) {
            !recalculateReferencedTimeslotData(
                timeslotId = id,
                userId = userId,
                manualDescriptionOverride = manualDescription,
            )
        }

        KIO.ok(ApiResponse.Created(id))
    }

    fun updateTimeslot(
        request: TimeslotRequest,
        userId: UUID,
        timeslotId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val existingTimeslot = !TimeslotRepo.getTimeslot(timeslotId).orDie()
            .onNullFail { EventDayError.TimeslotNotFound }

        val timeslotHasReference = hasReference(
            existingTimeslot.competitionReference,
            existingTimeslot.roundReference,
            existingTimeslot.matchReference,
        )

        val manualDescription = if (timeslotHasReference) {
            !resolveManualDescriptionForUpdate(timeslotId, request.description)
        } else {
            request.description.toMeaningfulDescription()
        }

        val fullDescription = composeDescription(manualDescription, null)

        !TimeslotRepo.update(timeslotId) {
            name = request.name
            description = fullDescription
            startTime = request.startTime
            endTime = request.endTime
            competitionReference = existingTimeslot.competitionReference
            roundReference = existingTimeslot.roundReference
            matchReference = existingTimeslot.matchReference
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { EventDayError.TimeslotNotFound }

        !TimeslotRepo.updateDescriptionParts(timeslotId, manualDescription, null).orDie()

        if (timeslotHasReference) {
            !recalculateReferencedTimeslotData(
                timeslotId = timeslotId,
                userId = userId,
                manualDescriptionOverride = manualDescription,
            )
        }

        KIO.ok(ApiResponse.NoData)
    }

    fun recalculateTimeslotEndTime(
        userId: UUID,
        timeslotId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !TimeslotRepo.getTimeslot(timeslotId).orDie()
            .onNullFail { EventDayError.TimeslotNotFound }

        !recalculateReferencedTimeslotData(
            timeslotId = timeslotId,
            userId = userId,
        )

        KIO.ok(ApiResponse.NoData)
    }

    fun deleteTimeslot(
        timeslotId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val timeslotExists = !TimeslotRepo.exists(timeslotId).orDie()
        if (!timeslotExists) KIO.fail(EventDayError.TimeslotNotFound)

        val deleted = !TimeslotRepo.delete(timeslotId).orDie()

        if (deleted < 1) {
            KIO.fail(EventDayError.EventDayNotFound)
        } else {
            noData
        }
    }

    fun getOwnTimeslotById(id:UUID) = KIO.comprehension {
        val data = !TimeslotRepo.findSelfIncludingTimeslotById(id).orDie()
        KIO.ok(data?.toDto())
    }
}
