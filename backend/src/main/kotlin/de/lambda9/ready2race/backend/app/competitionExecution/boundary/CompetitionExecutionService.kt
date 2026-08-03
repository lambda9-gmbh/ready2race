package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competition.control.toDto
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.competition.entity.EventDataForCompetitionResultsData
import de.lambda9.ready2race.backend.app.competitionDeregistration.control.CompetitionDeregistrationRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.*
import de.lambda9.ready2race.backend.app.competitionExecution.entity.*
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.documentTemplate.control.DocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toPdfTemplate
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventParticipant.control.EventParticipantRepo
import de.lambda9.ready2race.backend.app.eventParticipant.entity.EventParticipantError
import de.lambda9.ready2race.backend.app.matchResultImportConfig.control.MatchResultImportConfigRepo
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigError
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerConfigDto
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerConfigRequest
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.calls.comprehension.CallComprehensionScope
import de.lambda9.ready2race.backend.app.startListConfig.control.StartListConfigRepo
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigError
import de.lambda9.ready2race.backend.app.substitution.boundary.SubstitutionService
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.control.applyNewRound
import de.lambda9.ready2race.backend.app.substitution.control.toParticipantForExecutionDto
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.app.timecode.control.TimecodeRepo
import de.lambda9.ready2race.backend.app.timecode.control.toRecord
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.noDataResponse
import de.lambda9.ready2race.backend.csv.CSV
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.exists
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_FOR_EVENT
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.hr
import de.lambda9.ready2race.backend.hrTime
import de.lambda9.ready2race.backend.kio.onNullDie
import de.lambda9.ready2race.backend.kio.onTrueFail
import de.lambda9.ready2race.backend.parsing.Parser
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.ready2race.backend.singletonOrFallback
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.timecodePattern
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.validation.validators.Validator
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.anyOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.collection
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.notNull
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.oneOf
import de.lambda9.ready2race.backend.xls.CellParser.Companion.int
import de.lambda9.ready2race.backend.xls.CellParser.Companion.maybe
import de.lambda9.ready2race.backend.xls.CellParser.Companion.string
import de.lambda9.ready2race.backend.xls.CellParser.Companion.uuid
import de.lambda9.ready2race.backend.xls.XLS
import de.lambda9.ready2race.backend.xls.XLSReadError
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.*
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.collections.sortedBy
import de.lambda9.ready2race.backend.validation.fold

object CompetitionExecutionService {

    /** Separates competition short name and rating category where both share one export column. */
    private const val RATING_SEPARATOR = "·"

    fun getMatchesByEvent(
        eventId: UUID,
        currentlyRunning: Boolean? = null,
        withoutPlaces: Boolean? = null
    ): App<ServiceError, ApiResponse.ListDto<MatchForRunningStatusDto>> = KIO.comprehension {
        val matches = !CompetitionMatchRepo.getMatchesByEvent(eventId, currentlyRunning, withoutPlaces).orDie()
        KIO.ok(ApiResponse.ListDto(matches))
    }

    fun createNewRound(
        eventId: UUID,
        competitionId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId)
            .onTrueFail { CompetitionExecutionChallengeError.NotAChallengeEvent }

        var createFollowingRound = true
        while (createFollowingRound) {
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

            val (currentRound, nextRound) = getCurrentAndNextRound(setupRounds)

            // Number of teams placed into the round being created - used to resolve the bracket size N below.
            var justPlacedCount = 0

            if (currentRound == null) {
                // First Round

                val registrations = !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()

                !checkRoundCreation(true, setupRounds, null, nextRound, registrations)

                val deregisteredRegistrationIds =
                    !CompetitionDeregistrationRepo.getByRegistrations(registrations.map { it.id }).orDie()
                        .map { list -> list.map { it.competitionRegistration } }

                val nextRoundSetupMatches =
                    nextRound!!.setupMatches.sortedBy { it.weighting }

                val sortedRegistrations = registrations
                    .filter {
                        it.teamNumber != null  // Registrations without teamNumber are ignored. A confirmation Dialog makes aware of this behaviour
                    }
                    .sortedWith(
                        compareBy(
                            { deregisteredRegistrationIds.contains(it.id) },  // false (active) before true (deregistered)
                            { it.teamNumber }  // then sort by teamNumber within each group
                        ))


                val matchRecords = nextRoundSetupMatches
                    .filterIndexed { index, _ -> index < sortedRegistrations.size }
                    .map {
                        !it.applyCompetitionMatch(userId, null)
                    }
                !CompetitionMatchRepo.create(matchRecords).orDie()


                val seedingList = getSeedingList(nextRoundSetupMatches.map { it.teams }, registrations.size)


                val newTeamRecords = sortedRegistrations.mapIndexed { index, reg ->
                    val matchIndex = seedingList.indexOfFirst { it.contains(index + 1) }

                    val automaticFirstPlace =
                        !nextRound.required && seedingList[matchIndex].filter { it <= registrations.size }.size == 1

                    CompetitionMatchTeamRecord(
                        id = UUID.randomUUID(),
                        competitionMatch = matchRecords[matchIndex].competitionSetupMatch,
                        competitionRegistration = reg.id,
                        startNumber = seedingList[matchIndex].indexOfFirst { it == index + 1 } + 1,
                        place = if (automaticFirstPlace) 1 else null,
                        out = deregisteredRegistrationIds.contains(reg.id),
                        failed = false,
                        failedReason = null,
                        createdAt = LocalDateTime.now(),
                        createdBy = userId,
                        updatedAt = LocalDateTime.now(),
                        updatedBy = userId,
                    )
                }
                !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()
                justPlacedCount = newTeamRecords.count { !it.out!! }

                if (newTeamRecords.size > nextRoundSetupMatches.size || nextRound.required || nextRound.nextRound == null
                ) {
                    createFollowingRound = false
                }

            } else {
                // Following Round

                !checkRoundCreation(true, setupRounds, currentRound, nextRound, null)

                // --- Collect current round data

                val currentRoundMatches = currentRound.setupMatches
                    .sortedBy { it.weighting }
                    .map { setupMatch ->
                        setupMatch to currentRound.matches.find { match -> match.competitionSetupMatch == setupMatch.id }
                    }
                val nextRoundSetupMatches =
                    nextRound!!.setupMatches.sortedBy { it.weighting }

                val currentRoundOutcomes =
                    getSeedingList(
                        currentRoundMatches.map { it.second?.teams?.size },
                        nextRoundSetupMatches.sumOf { it.teams ?: 0 }
                    )

                val currentTeamsWithOutcome =
                    currentRoundMatches.filter { it.second != null }.mapIndexed { matchIdx, match ->
                        match.second!!.teams
                            .sortedWith(compareBy<CompetitionMatchTeamWithRegistration> { team ->
                                team.place ?: Int.MAX_VALUE
                            }.thenBy { team -> team.startNumber }) // This is required for teams that are deregistered but would still move on to the next round (f.e. losers bracket / both teams deregistered)
                            .mapIndexed { teamIdx, team ->
                                team to currentRoundOutcomes[matchIdx][teamIdx]
                            }
                    }.flatten()

                // --- Create next round

                val matchRecords = nextRoundSetupMatches
                    .filterIndexed { index, _ -> index < currentTeamsWithOutcome.size }
                    .map {
                        !it.applyCompetitionMatch(userId, null)
                    }
                !CompetitionMatchRepo.create(matchRecords).orDie()

                val nextRoundSetupParticipants =
                    !CompetitionSetupParticipantRepo.get(nextRoundSetupMatches.map { it.id }).orDie()

                val currentTeamsToParticipantId = currentTeamsWithOutcome.map { cTeam ->
                    cTeam.first to nextRoundSetupParticipants.find { p -> p.seed == cTeam.second } // Match Outcomes with ParticipantSeeds
                }.filter { team -> team.second != null } // Filter teams that have not made it to next round


                val newTeamRecords = currentTeamsToParticipantId.map { team ->

                    val prevTeam = team.first
                    val nextRoundTeam = team.second!!

                    val automaticFirstPlace =
                        !nextRound.required && !prevTeam.deregistered && !prevTeam.out && !prevTeam.failed &&
                            currentTeamsToParticipantId.filter {
                                it.second!!.competitionSetupMatch == nextRoundTeam.competitionSetupMatch && !it.first.out && !it.first.failed && !it.first.deregistered
                            }.size == 1

                    CompetitionMatchTeamRecord(
                        id = UUID.randomUUID(),
                        competitionMatch = nextRoundTeam.competitionSetupMatch!!,
                        competitionRegistration = prevTeam.competitionRegistration,
                        startNumber = nextRoundTeam.ranking,
                        place = if (automaticFirstPlace) 1 else null,
                        out = prevTeam.deregistered || prevTeam.out || prevTeam.failed,
                        failed = false,
                        failedReason = null,
                        createdAt = LocalDateTime.now(),
                        createdBy = userId,
                        updatedAt = LocalDateTime.now(),
                        updatedBy = userId,
                    )
                }
                !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()
                justPlacedCount = newTeamRecords.count { !it.out!! }

                // Carry over all substitutions to the new round
                val currentRoundSubstitutions = !SubstitutionRepo.getByRound(currentRound.setupRoundId).orDie()
                val substitutionsRelevantForNextRound = currentRoundSubstitutions.map { record ->
                    !record.applyNewRound(nextRound.setupRoundId)
                }
                !SubstitutionRepo.insert(substitutionsRelevantForNextRound).orDie()

                if (newTeamRecords.filter { !it.out!! }.size > nextRoundSetupMatches.size || nextRound.required || nextRound.nextRound == null
                ) {
                    createFollowingRound = false
                }
            }

            // Apply the per-participant-count name / execution-order overrides to the round just created.
            // N (bracket size) is fixed at the qualification -> bracket transition: the number of teams entering the
            // first non-qualification round. Teams eliminated during qualification are carried into the bracket only
            // as out byes, so they must be excluded from N (justPlacedCount already counts active teams only).
            // Drop-outs later in the bracket likewise become byes and do not change N.
            if (nextRound != null && !nextRound.isQualification) {
                val bracketStart = sortRounds(setupRounds).firstOrNull { !it.isQualification }
                val n = when {
                    bracketStart == null -> 0
                    bracketStart.setupRoundId == nextRound.setupRoundId -> justPlacedCount
                    else -> bracketStart.matches.sumOf { match -> match.teams.count { !it.out } }
                }
                if (n > 0) {
                    val namings =
                        !CompetitionSetupMatchNamingRepo.getForRoundAndCount(nextRound.setupRoundId, n).orDie()
                    namings.forEach { naming ->
                        nextRound.setupMatches.find { it.weighting == naming.matchWeighting }?.let { setupMatch ->
                            !CompetitionSetupMatchRepo.updateNameAndOrder(
                                setupMatch.id,
                                naming.name,
                                naming.executionOrder
                            ).orDie()
                        }
                    }
                }
            }
        }

        noData
    }

    fun getProgress(
        eventId: UUID,
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.Dto<CompetitionExecutionProgressDto>> =
        KIO.comprehension {
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

            val currentAndNextRound = getCurrentAndNextRound(setupRounds)

            val registrations = !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()


            val canNotCreateRoundReasons = !checkRoundCreation(
                false,
                setupRounds,
                currentAndNextRound.first,
                currentAndNextRound.second,
                registrations,
            )

            val sortedRounds = sortRounds(setupRounds)

            val event = !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

            sortedRounds.filter { it.matches.isNotEmpty() }.traverse { round ->
                round.copy(matches = round.matches.map { match -> match.copy(teams = match.teams.filter { !it.out }) })
                    .toCompetitionRoundDto(event.mixedTeamTerm)
            }.map {
                ApiResponse.Dto(
                    CompetitionExecutionProgressDto(
                        rounds = it,
                        canNotCreateRoundReasons,
                        isChallengeEvent = event.challengeEvent!!
                    )
                )
            }
        }

    fun sortRounds(
        setupRounds: List<CompetitionSetupRoundWithMatches>
    ): List<CompetitionSetupRoundWithMatches> {
        val sortedRounds: MutableList<CompetitionSetupRoundWithMatches> = mutableListOf()
        fun addRoundToSortedList(r: CompetitionSetupRoundWithMatches?) {
            if (r != null) {
                sortedRounds.add(0, r)

                addRoundToSortedList(setupRounds.firstOrNull { it.nextRound == r.setupRoundId })
            }
        }
        addRoundToSortedList(setupRounds.firstOrNull { it.nextRound == null })
        return sortedRounds
    }

    private fun checkRoundCreation(
        failOnError: Boolean,
        rounds: List<CompetitionSetupRoundWithMatches>,
        currentRound: CompetitionSetupRoundWithMatches?,
        nextRound: CompetitionSetupRoundWithMatches?,
        registrations: List<CompetitionRegistrationRecord>?
    ): App<CompetitionExecutionError, List<CompetitionExecutionCanNotCreateRoundReason>> = KIO.comprehension {
        val reasons = mutableListOf<Pair<CompetitionExecutionCanNotCreateRoundReason, CompetitionExecutionError>>()

        val nextRoundSetupMatches = nextRound?.setupMatches


        if (rounds.isEmpty()) {
            reasons.add(CompetitionExecutionCanNotCreateRoundReason.NO_ROUNDS_IN_SETUP to CompetitionExecutionError.NoRoundsInSetup)
        } else {
            if (nextRound == null) {
                reasons.add(CompetitionExecutionCanNotCreateRoundReason.ALL_ROUNDS_CREATED to CompetitionExecutionError.AllRoundsCreated)
            }

            if (nextRoundSetupMatches?.isEmpty() == true)
                reasons.add(CompetitionExecutionCanNotCreateRoundReason.NO_SETUP_MATCHES to CompetitionExecutionError.NoSetupMatchesInRound)

        }


        if (currentRound == null) {
            if (registrations.isNullOrEmpty())
                reasons.add(CompetitionExecutionCanNotCreateRoundReason.NO_REGISTRATIONS to CompetitionExecutionError.NoRegistrations)
            else {
                if (nextRoundSetupMatches != null) {
                    if (nextRoundSetupMatches.find { it.teams == null } == null && nextRoundSetupMatches.sumOf {
                            it.teams ?: 0
                        } < registrations.size) {
                        reasons.add(CompetitionExecutionCanNotCreateRoundReason.NOT_ENOUGH_TEAM_SPACE to CompetitionExecutionError.NotEnoughTeamSpace)

                    }
                }

                if (registrations.none { it.teamNumber != null })
                    reasons.add(CompetitionExecutionCanNotCreateRoundReason.REGISTRATIONS_NOT_FINALIZED to CompetitionExecutionError.RegistrationsNotFinalized)
            }


        } else {

            val placesAreMissing = currentRound.matches.any { match ->
                !match.teams.map { it.place }
                    .containsAll((1..match.teams.filter { !it.deregistered && !it.failed && !it.out }.size).toList())
            }

            if (placesAreMissing)
                reasons.add(CompetitionExecutionCanNotCreateRoundReason.NOT_ALL_PLACES_SET to CompetitionExecutionError.NotAllPlacesSet)
        }

        if (failOnError && reasons.isNotEmpty()) {
            return@comprehension KIO.fail(reasons.first().second)
        } else {
            KIO.ok(
                reasons.map { it.first }
            )
        }
    }

    fun updateMatchData(
        eventId: UUID,
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchRequest
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val setupMatch =
            !CompetitionSetupMatchRepo.get(matchId).orDie().onNullFail { CompetitionExecutionError.MatchNotFound }
        val setupRound = !CompetitionSetupRoundRepo.get(setupMatch.competitionSetupRound).orDie()
            .onNullFail { CompetitionExecutionError.RoundNotFound }

        val teamRecords = !CompetitionMatchTeamRepo.getByMatch(matchId).orDie()
        val (expectedTeams, outTeams) = teamRecords.partition { !it.out!! }

        if (!setupRound.required && teamRecords.size == 1) {
            return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)
        }

        !CompetitionMatchRepo.update(matchId) {
            startTime = request.startTime
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        if (expectedTeams.size != request.teams.size) {
            return@comprehension KIO.fail(CompetitionExecutionError.TeamsNotMatching)
        }
        expectedTeams.forEach { tr ->
            if (request.teams.filter { it.registrationId == tr.competitionRegistration }.size != 1)
                return@comprehension KIO.fail(CompetitionExecutionError.TeamsNotMatching)
        }

        !teamRecords.traverse { team ->
            CompetitionMatchTeamRepo.update(team) {
                startNumber = (team.startNumber * -1)
            }.orDie()
        }

        val highestStartNumber = request.teams.maxOfOrNull { it.startNumber } ?: 0
        val outStartNumbers =
            outTeams.sortedBy { it.startNumber }.mapIndexed { index, team -> team.id to index + highestStartNumber + 1 }
                .toMap()

        !teamRecords.traverse { team ->
            CompetitionMatchTeamRepo.update(team) {
                startNumber =
                    if (team.out!!) {
                        outStartNumbers[team.id]!!
                    } else {
                        request.teams.find { it.registrationId == team.competitionRegistration }!!.startNumber // Can be guaranteed by previous checks
                    }
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
        }

        noData
    }

    private fun checkUpdateMatchResult(
        competitionId: UUID,
        matchId: UUID,
        /**
         * A single team in a round that is not required is a bye - it moves on without racing, so there
         * is no result to record. Callers that can say something more useful about that than "locked"
         * pass their own error.
         */
        byeError: ServiceError = CompetitionExecutionError.MatchResultsLocked,
    ): App<ServiceError, CompetitionMatchWithTeams> = KIO.comprehension {

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        !KIO.failOn(setupRounds.flatMap { it.setupMatches.toList() }
            .find { it.id == matchId } == null) { CompetitionExecutionError.MatchNotFound }

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)

        val match = currentRound.matches.find { it.competitionSetupMatch == matchId }
            ?: return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)

        !KIO.failOn(!currentRound.required && match.teams.size == 1) { byeError }


        KIO.ok(match)
    }

    private fun prepareForNewPlaces(
        matchId: UUID,
        userId: UUID,
        /**
         * Whether the match is done. Partial results (only some boats finished) leave the
         * match running, so the live views keep showing it as the current race.
         */
        stopRunning: Boolean = true,
    ): App<Nothing, Unit> = KIO.comprehension {

        if (stopRunning) {
            !CompetitionMatchRepo.update(matchId) {
                currentlyRunning = false
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie()
        }

        !CompetitionMatchTeamRepo.updateManyByMatch(matchId) {
            place = null
        }.orDie()

        unit
    }

    fun updateMatchResult(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchResultRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        !checkUpdateMatchResult(competitionId, matchId)

        // A submission may cover only part of the field (first boats across the line). The match
        // counts as done only once every participating team has a result.
        val matchTeams = !CompetitionMatchTeamRepo.getByMatch(matchId).orDie()
        val resultsComplete = matchTeams
            .filter { it.out != true }
            .all { team -> request.teamResults.any { it.registrationId == team.competitionRegistration } }

        !prepareForNewPlaces(matchId, userId, stopRunning = resultsComplete)

        val noPlaces = request.teamResults.filter { !it.failed }.any { it.place == null }

        // Validate places are continuous when provided
        if (!noPlaces) {
            val places = request.teamResults.filter { !it.failed }.mapNotNull { it.place }.sorted()
            places.forEachIndexed { index, place ->
                val expected = index + 1
                !KIO.failOn(expected != place) { CompetitionExecutionError.PlacesNotContinuous }
            }
        }

        val calculatedPlaces: List<Pair<UUID, Timecode?>> =
            request.teamResults.filter { !it.failed }
                .map { result ->
                    result.registrationId to result.timeString?.let { timestring -> (!Parser.timecode(timestring) { it.orDie() }) }
                }
                .sortedBy { it.second?.millis }

        request.teamResults.traverse { result ->
            KIO.comprehension {

                val record =
                    !CompetitionMatchTeamRepo.getByMatchAndRegistrationId(matchId, result.registrationId).orDie()
                        .onNullFail { CompetitionExecutionError.MatchTeamNotFound }
                !TimecodeRepo.delete(record.id).orDie()
                val timecode = if (!result.failed && result.timeString != null) {
                    !TimecodeRepo.create(
                        calculatedPlaces.find { (id) -> id == result.registrationId }!!.second!!.toRecord(record.id)
                    ).orDie()
                } else null

                CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, result.registrationId) {
                    this.place = if (noPlaces) {
                        (calculatedPlaces.indexOfFirst { (id, _) -> id == result.registrationId } + 1).takeIf { it > 0 }
                    } else {
                        result.place
                    }
                    this.placesCalculated = noPlaces
                    this.timecode = timecode
                    this.failed = result.failed
                    this.failedReason = result.failedReason
                    // Nur ausgewiesen, nie verrechnet: die erfasste Zeit gilt wie eingetragen.
                    // Achtung für später: sobald eine externe Zeitmessung (RaceClocker) Strafen
                    // liefert, ist sie die Quelle der Wahrheit und überschreibt diesen Wert - eine
                    // hier eingetragene Strafe geht dann verloren. Wer beide Wege erlauben will,
                    // braucht vorher eine Regel, welche Quelle gewinnt.
                    this.penaltySeconds = result.penaltySeconds
                    this.penaltyNote = result.penaltyNote
                    updatedBy = userId
                    updatedAt = LocalDateTime.now()
                }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }

            }
        }.noDataResponse()
    }

    fun updateMatchResultByFile(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        file: File,
        request: UploadMatchResultRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val match = !checkUpdateMatchResult(competitionId, matchId)
        !prepareForNewPlaces(matchId, userId)

        val config = !MatchResultImportConfigRepo.get(request.config).orDie()
            .onNullFail { MatchResultImportConfigError.NotFound }

        val identifierColumn = config.colTeamRegistrationId

        val iStream = file.bytes.inputStream()

        val teams = !XLS.read(iStream) {
            val place = !optionalCell(config.colTeamPlace, maybe(int))
            // The timing tooling (e.g. Webscorer) writes a no-result status (DNF / DNS / DSQ / ...) into
            // the time column in place of a finish time. A non-blank time cell that does not parse as a
            // timecode is therefore treated as the no-result reason rather than as a time.
            val timeCell = (!optionalCell(config.colTeamTime, string))?.takeIf { it.isNotBlank() }
            val timeIsValid = timeCell != null && timecodePattern.matches(timeCell)
            ParsedTeamResult(
                registrationId = !cell(identifierColumn, uuid),
                startNumber = !optionalCell(config.colTeamStartNumber, int),
                place = place,
                time = timeCell?.takeIf { timeIsValid },
                noResultReason = timeCell?.takeUnless { timeIsValid }
            )
        }.mapError {
            when (it) {
                is XLSReadError.CellError.ColumnUnknown -> CompetitionExecutionError.ResultUploadError.ColumnUnknown(it.expected)
                is XLSReadError.CellError.ParseError.CellBlank -> CompetitionExecutionError.ResultUploadError.CellBlank(
                    it.row,
                    it.col
                )

                is XLSReadError.CellError.ParseError.WrongCellType -> CompetitionExecutionError.ResultUploadError.WrongCellType(
                    it.row,
                    it.col,
                    it.actual.name,
                    it.expected.name
                )

                is XLSReadError.CellError.ParseError.UnparsableStringValue -> CompetitionExecutionError.ResultUploadError.UnparsableString(
                    it.row,
                    it.col,
                    it.value
                )

                XLSReadError.FileError -> CompetitionExecutionError.ResultUploadError.FileError
                XLSReadError.NoHeaders -> CompetitionExecutionError.ResultUploadError.NoHeaders
            }
        }

        !noDuplicates(teams.mapNotNull { it.startNumber }).fold(
            onValid = { unit },
            onInvalid = {
                when (it) {
                    is ValidationResult.Invalid.Duplicates -> KIO.fail(
                        CompetitionExecutionError.ResultUploadError.Invalid.DuplicatedStartNumbers(
                            it
                        )
                    )

                    else -> KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.Unexpected(it))
                }
            }
        )

        !noDuplicates(teams.map { it.registrationId }).fold(
            onValid = { unit },
            onInvalid = {
                when (it) {
                    is ValidationResult.Invalid.Duplicates -> KIO.fail(
                        CompetitionExecutionError.ResultUploadError.Invalid.DuplicatedTeams(
                            it
                        )
                    )

                    else -> KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.Unexpected(it))
                }
            }
        )

        val places = teams.map { it.place }

        !noDuplicates(places).fold(
            onValid = { unit },
            onInvalid = {
                when (it) {
                    is ValidationResult.Invalid.Duplicates -> KIO.fail(
                        CompetitionExecutionError.ResultUploadError.Invalid.DuplicatedPlaces(
                            it
                        )
                    )

                    else -> KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.Unexpected(it))
                }
            }
        )

        !allOf(
            anyOf(
                collection(
                    oneOf(
                        Validator.select(notNull, ParsedTeamResult::place),
                        Validator.select(notNull, ParsedTeamResult::noResultReason)
                    )
                ),
                collection(
                    Validator.select(isNull, ParsedTeamResult::place)
                ),
            ),
            anyOf(
                collection(
                    oneOf(
                        Validator.select(notNull, ParsedTeamResult::time),
                        Validator.select(notNull, ParsedTeamResult::noResultReason)
                    )
                ),
                collection(
                    Validator.select(isNull, ParsedTeamResult::time)
                ),
            )
        )(teams).fold(
            onValid = { unit },
            onInvalid = {
                KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.DataInListIncomplete(it))
            }
        )

        !collection(
            oneOf(
                anyOf(
                    Validator.select(notNull, ParsedTeamResult::place),
                    Validator.select(notNull, ParsedTeamResult::time)
                ),
                Validator.select(notNull, ParsedTeamResult::noResultReason)
            )
        )(teams).fold(
            onValid = { unit },
            onInvalid = {
                KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.ResultNotFailedAndNoData(it))
            }
        )

        // TODO: disabled for now, because it helps with parallel matches (can upload results to multiple matches with the same file)
        //!KIO.failOn(teams.size != match.teams.size) { CompetitionExecutionError.ResultUploadError.WrongTeamCount(teams.size, match.teams.size) }

        // TODO: disabled for now, because it forbids upload of same results for parallel races
        /*places.filterNotNull().sorted().forEachIndexed { index, place ->
            val expected = index + 1
            !KIO.failOn(expected != place) { CompetitionExecutionError.ResultUploadError.Invalid.PlacesUncontinuous(place, expected) }
        }*/
        applyParsedTeamResults(match, matchId, teams, userId)
    }

    /**
     * Writes externally timed results onto a match. Shared by the spreadsheet upload and the
     * RaceClocker pull; both arrive here with the same [ParsedTeamResult] shape.
     *
     * Expects [prepareForNewPlaces] to have run already.
     */
    private fun applyParsedTeamResults(
        match: CompetitionMatchWithTeams,
        matchId: UUID,
        teams: List<ParsedTeamResult>,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        // TODO: instead for now, we sort the places and give first place to smallest place in expected start numbers maintaining teams with place == null
        val validTeams =
            teams.filter { team -> match.teams.any { team.registrationId == it.competitionRegistration && !it.deregistered } }
        val (teamWithoutPlace, teamWithPlace) = validTeams.partition { it.place == null }
        val correctedTeams = teamWithoutPlace + teamWithPlace.sortedBy { it.place!! }
            .mapIndexed { idx, res -> res.copy(place = idx + 1) }


        val calculatedPlaces: List<Pair<UUID, Timecode?>> =
            correctedTeams.filter { it.noResultReason == null }
                .map { result ->
                    result.registrationId to result.time?.let { timeString -> (!Parser.timecode(timeString) { it.orDie() }) }
                }.sortedBy { it.second?.millis }

        val noPlaces = correctedTeams.filter { it.noResultReason == null }.any { it.place == null }

        // Write back the (potentially externally changed) start numbers. The stable team identifier is
        // the source of truth for matching, so we adopt whatever start numbers the external timing tooling
        // assigned. Start numbers are negated first to avoid transient violations of the unique
        // (competition_match, start_number) index while reassigning. Match teams that are not part of the
        // imported file (e.g. deregistered/out teams) keep a unique number after the highest imported one.
        if (correctedTeams.isNotEmpty() && correctedTeams.all { it.startNumber != null }) {
            val allMatchTeamRecords = !CompetitionMatchTeamRepo.getByMatch(matchId).orDie()
            val importedStartNumberByRegistration = correctedTeams.associate { it.registrationId to it.startNumber!! }

            !allMatchTeamRecords.traverse { team ->
                CompetitionMatchTeamRepo.update(team) {
                    startNumber = team.startNumber * -1
                }.orDie()
            }

            var fallbackStartNumber = importedStartNumberByRegistration.values.maxOrNull() ?: 0
            !allMatchTeamRecords.sortedBy { it.startNumber }.traverse { team ->
                val newStartNumber = importedStartNumberByRegistration[team.competitionRegistration]
                    ?: (++fallbackStartNumber)
                CompetitionMatchTeamRepo.update(team) {
                    startNumber = newStartNumber
                    updatedBy = userId
                    updatedAt = LocalDateTime.now()
                }.orDie()
            }
        }

        correctedTeams.traverse { result ->

            KIO.comprehension {
                val registrationId = result.registrationId

                val record =
                    !CompetitionMatchTeamRepo.getByMatchAndRegistrationId(matchId, registrationId).orDie()
                        .onNullFail { CompetitionExecutionError.MatchTeamNotFound }
                !TimecodeRepo.delete(record.id).orDie()
                val timecode = if (result.time != null) {
                    !TimecodeRepo.create(
                        calculatedPlaces.find { (id) -> id == result.registrationId }!!.second!!.toRecord(record.id)
                    ).orDie()
                } else null

                // TODO: better error for frontend

                CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, registrationId) {
                    this.place = if (noPlaces) {
                        (calculatedPlaces.indexOfFirst { (id, _) -> id == result.registrationId } + 1).takeIf { it > 0 }
                    } else {
                        result.place
                    }
                    this.placesCalculated = noPlaces
                    this.failed = result.noResultReason != null
                    this.failedReason = result.noResultReason
                    this.timecode = timecode
                    updatedBy = userId
                    updatedAt = LocalDateTime.now()
                }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
            }
        }.noDataResponse()


    }

    fun getRaceClockerConfig(
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.Dto<RaceClockerConfigDto>> = KIO.comprehension {

        val competition = !CompetitionRepo.getRecordById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }

        KIO.ok(
            ApiResponse.Dto(
                RaceClockerConfigDto(
                    timeTrialResultsUrl = competition.raceclockerTtResultsUrl,
                    heatsResultsUrl = competition.raceclockerHeatsResultsUrl,
                )
            )
        )
    }

    fun updateRaceClockerConfig(
        competitionId: UUID,
        userId: UUID,
        request: RaceClockerConfigRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        // Stored normalised (scheme filled in, http lifted to https), so the config dialog shows
        // afterwards what the pull actually requests. Blank means "not configured" - keeping empty
        // strings would make the pull fail later with an unhelpful URL error instead of the clear
        // "no URL configured" one.
        val timeTrialUrl = request.timeTrialResultsUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let { (!RaceClockerFeed.normalizeUrl(it)).toString() }
        val heatsUrl = request.heatsResultsUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let { (!RaceClockerFeed.normalizeUrl(it)).toString() }

        !CompetitionRepo.update(competitionId) {
            raceclockerTtResultsUrl = timeTrialUrl
            raceclockerHeatsResultsUrl = heatsUrl
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().onNullFail { CompetitionError.CompetitionNotFound }

        noData
    }

    /**
     * Pulls the results of a single match from RaceClocker's public results feed.
     *
     * Counterpart to [updateMatchResultByFile]: same destination, but the data is fetched instead of
     * uploaded. Rows are tied to teams by an identifier that travelled out in the start list and comes
     * back in RaceClocker's "Extra info" (see [assignFeedRows]) - the bib cannot serve as the key,
     * since it is only a lane number within a match and repeats across heats.
     *
     * Nothing is written until every check has passed, so a rejected pull leaves the match untouched
     * and can simply be repeated once RaceClocker has been corrected.
     */
    suspend fun CallComprehensionScope.updateMatchResultFromRaceClocker(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val match = !checkUpdateMatchResult(competitionId, matchId, byeError = RaceClockerError.MatchIsBye)

        val target = !CompetitionMatchRepo.getForRaceClockerPull(matchId).orDie()
            .onNullFail { CompetitionExecutionError.MatchNotFound }

        val urls = target.candidateUrls
        if (urls.isEmpty()) return KIO.fail(RaceClockerError.UrlMissing)

        val teams = match.teams.filter { !it.deregistered }

        // The round type only decides which race to look into *first*. Is a round timed as a time trial
        // without being marked as a qualification round (or the other way around), the match is simply
        // found in the other race instead of failing with a misleading error.
        var rowsByTeam: Map<UUID, List<RaceClockerFeedRow>> = emptyMap()
        for (rawUrl in urls) {
            val url = !RaceClockerFeed.normalizeUrl(rawUrl)
            val rows = !RaceClockerFeed.fetch(RaceClockerFeed.feedUrl(url))
            rowsByTeam = assignFeedRows(rows, teams, target.waveName)
            if (rowsByTeam.isNotEmpty()) break
        }

        if (rowsByTeam.isEmpty()) return KIO.fail(RaceClockerError.MatchNotInFeed(urls))

        // RaceClocker only ever inserts, it never updates: importing the same start list twice leaves
        // duplicate crews behind. Picking one of them silently would be a coin flip, so we refuse and
        // let the user clean up there.
        val duplicates = rowsByTeam.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            return KIO.fail(
                RaceClockerError.DuplicateTeams(target.waveName, duplicates.values.map { it.first().name })
            )
        }

        // Crews that have not been timed yet are skipped rather than treated as an error, so the pull
        // can be repeated as the heat progresses.
        val timed = rowsByTeam.mapNotNull { (registrationId, rows) ->
            rows.single().takeIf { it.result != null }?.let { registrationId to it }
        }
        if (timed.isEmpty()) return KIO.fail(RaceClockerError.NoResults(target.waveName))

        !prepareForNewPlaces(matchId, userId)

        val parsed = timed.map { (registrationId, row) ->
            ParsedTeamResult(
                registrationId = registrationId,
                startNumber = row.bib,
                // The feed carries no rank; places are derived from the times further down.
                place = null,
                time = row.time,
                noResultReason = row.noResultReason,
            )
        }

        return applyParsedTeamResults(match, matchId, parsed, userId)
    }

    /**
     * Assigns feed rows to the teams of a match, keyed by registration id - what [ParsedTeamResult]
     * expects downstream.
     *
     * The primary key is the competition match team id: unique per team *and* round, so a row can be
     * assigned without knowing anything about waves. That matters because waves are renamed and merged
     * in RaceClocker on race day ("AF4 & AF2" for a joint start), which is invisible here.
     *
     * Start lists exported before that column existed carry only the registration id, which is unique
     * per team but repeats across the rounds that share one RaceClocker race. Those rows are narrowed
     * down by the wave name - but only as long as the exported name still occurs in the feed. Once it
     * was renamed there, matching by registration alone is the better of the two guesses.
     */
    private fun assignFeedRows(
        rows: List<RaceClockerFeedRow>,
        teams: List<CompetitionMatchTeamWithRegistration>,
        waveName: String?,
    ): Map<UUID, List<RaceClockerFeedRow>> {

        val byMatchTeam = teams.associate { team ->
            team.competitionRegistration to rows.filter { team.id in it.ids }
        }.filterValues { it.isNotEmpty() }

        if (byMatchTeam.isNotEmpty()) return byMatchTeam

        val candidates = if (waveName != null && rows.any { it.wave == waveName }) {
            rows.filter { it.wave == waveName }
        } else {
            rows
        }

        return teams.associate { team ->
            team.competitionRegistration to candidates.filter { team.competitionRegistration in it.ids }
        }.filterValues { it.isNotEmpty() }
    }

    fun updateMatchRunningState(
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchRunningStateRequest,
        eventId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        !CompetitionMatchRepo.exists(matchId).orDie().onNullFail { CompetitionExecutionError.MatchNotFound }

        !CompetitionMatchRepo.update(matchId) {
            currentlyRunning = request.currentlyRunning
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        noData
    }


    fun deleteCurrentRound(
        competitionId: UUID,
        eventId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)

        !SubstitutionRepo.deleteBySetupRoundId(currentRound.setupRoundId).orDie()

        val matchIds = currentRound.matches.map { it.competitionSetupMatch }

        val deleted = !CompetitionMatchRepo.delete(matchIds).orDie()

        !CompetitionMatchTeamRepo.deleteTimecodesByMatchIds(matchIds).orDie()

        if (deleted < 1) {
            KIO.fail(CompetitionExecutionError.RoundNotFound)
        } else {
            noData
        }
    }


    fun getCurrentAndNextRound(
        rounds: List<CompetitionSetupRoundWithMatches>
    ): Pair<CompetitionSetupRoundWithMatches?, CompetitionSetupRoundWithMatches?> {
        val finalRound = rounds.find { it.nextRound == null }
        var currentRound = finalRound
        for (i in rounds) {
            if (currentRound?.matches?.isEmpty() == true) {
                currentRound = rounds.find { it.nextRound == currentRound?.setupRoundId }
            }
        }

        val nextRound = if (currentRound == null) {
            rounds.find { r1 -> rounds.find { r2 -> r1.setupRoundId == r2.nextRound } == null }
        } else {
            rounds.find { it.setupRoundId == currentRound.nextRound }
        }

        return currentRound to nextRound
    }

    private fun getSeedingList(
        currentRoundTeams: List<Int?>,
        maxTeamsNeeded: Int
    ): List<List<Int>> {
        val currentRoundHighestTeamCount =
            getHighestTeamCount(currentRoundTeams, maxTeamsNeeded)


        val currentRoundSeedings = currentRoundTeams.map { mutableListOf<Int>() }

        var seedingsTaken = 0
        for (i in 0..<currentRoundHighestTeamCount) {
            fun addToList(index: Int) {
                if ((currentRoundTeams[index]
                        ?: 0) > currentRoundSeedings[index].size || (currentRoundTeams[index] == null && seedingsTaken < maxTeamsNeeded)
                ) {
                    seedingsTaken++
                    currentRoundSeedings[index].add(seedingsTaken)
                }
            }

            if (i % 2 == 0) {
                for (s in currentRoundTeams.indices) addToList(s)
            } else {
                for (s in currentRoundTeams.size - 1 downTo 0) addToList(s)
            }
        }

        return currentRoundSeedings
    }

    private fun getHighestTeamCount(
        teams: List<Int?>,
        maxTeamsNeeded: Int
    ): Int {
        val highestDefinedTeamCount = teams.maxByOrNull { it ?: 0 } ?: 0

        val teamsForEachUndefinedTeams = if (teams.any { it == null }) {
            maxTeamsNeeded - teams.filterNotNull().sum() / teams.filter { it == null }.size
        } else 0

        return if (highestDefinedTeamCount > teamsForEachUndefinedTeams) {
            highestDefinedTeamCount
        } else {
            teamsForEachUndefinedTeams
        }
    }

    fun computeCompetitionPlaces(
        competitionId: UUID,
    ): App<ServiceError, List<Pair<CompetitionMatchTeamWithRegistration, Int>>> = KIO.comprehension {
        val setupRoundRecords = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val setupRounds = sortRounds(setupRoundRecords)


        val roundsWithTeamsToPlaces =
            setupRounds.filterIndexed { roundIdx, round -> // filters out rounds for which there was no following round created yet
                if (roundIdx < setupRounds.size - 1) {
                    setupRounds[roundIdx + 1].matches.isNotEmpty()
                } else round.matches.flatMap { it.teams }
                    .none { it.place == null && !it.deregistered && !it.out && !it.failed }
            }.mapIndexed { roundIdx, round ->

                val isLastRound = roundIdx >= setupRounds.size - 1

                val sortedRoundMatches =
                    round.matches.sortedBy { m -> round.setupMatches.first { it.id == m.competitionSetupMatch }.weighting }

                val nonAdvancingTeamsToMatchIndex = sortedRoundMatches.flatMapIndexed { matchIdx, match ->
                    match.teams.map { it to matchIdx }
                }
                    .filter { (team, _) -> // Filter out teams that will move on to the next round or have not yet a set place in the last round / are not deregistered
                        if (!isLastRound) {
                            setupRounds[roundIdx + 1].matches.toList().flatMap { m -> m.teams.toList() }
                                .find { it.competitionRegistration == team.competitionRegistration } == null
                        } else team.place != null || team.deregistered || team.out || team.failed
                    }

                val seedingList =
                    if (round.placesOption != CompetitionSetupPlacesOption.ASCENDING.name || round.placesOption != CompetitionSetupPlacesOption.CUSTOM.name) { // Only relevant if the placesOption is "ascending" or "custom"
                        getSeedingList(
                            currentRoundTeams = round.setupMatches.sortedBy { it.weighting }.map { it.teams },
                            maxTeamsNeeded = setupRounds.getOrNull(roundIdx + 1)?.setupMatches?.sumOf { it.teams ?: 0 }
                                ?: 0)
                    } else null


                val teamsToPlaces = nonAdvancingTeamsToMatchIndex.map { (team, matchIndex) ->

                    val teamsInSameMatch = nonAdvancingTeamsToMatchIndex.filter { it.second == matchIndex }
                    // Place can only be null here if this team is deregistered
                    val (cancelledTeamsInSameMatch, teamsWithPlacesInSameMatch) =
                        teamsInSameMatch.partition { (t, _) -> t.deregistered || t.out || t.failed }

                    val realPlace = team.place
                        ?: (teamsWithPlacesInSameMatch.size
                            + (cancelledTeamsInSameMatch
                            .sortedBy { it.first.startNumber }
                            .map { it.first.competitionRegistration }
                            .indexOf(team.competitionRegistration))
                            + 1)


                    val teamToPlace = when (round.placesOption) {
                        CompetitionSetupPlacesOption.EQUAL.name -> {
                            team to if (!isLastRound) {
                                setupRounds[roundIdx + 1].matches.flatMap { m -> m.teams.toList() }.size + 1 // Place is one higher than the count of participants in the next round
                            } else 1 // 1 if this is the final round
                        }

                        CompetitionSetupPlacesOption.ASCENDING.name ->
                            team to seedingList!![matchIndex][realPlace - 1]

                        else ->
                            team to round.places.first { it.roundOutcome == seedingList!![matchIndex][realPlace - 1] }.place
                    }
                    teamToPlace
                }

                teamsToPlaces
            }


        val result = roundsWithTeamsToPlaces.flatten()

        KIO.ok(result)
    }

    fun getCompetitionPlaces(
        eventId: UUID,
        competitionId: UUID,
        scope: Privilege.Scope?,
    ): App<ServiceError, ApiResponse.ListDto<CompetitionTeamPlaceDto>> = KIO.comprehension {

        !EventRepo.getScoped(eventId, scope).orDie().onNullFail { EventError.NotFound }

        computeCompetitionPlaces(competitionId)
            .andThen { places ->
                places
                    .sortedBy { it.second }
                    .traverse { it.first.toCompetitionTeamPlaceDto(it.second) }
            }.map {
                ApiResponse.ListDto(
                    it
                )
            }
    }

    fun getActuallyParticipatingParticipants(
        teamParticipants: List<ParticipantForExecutionDto>,
        substitutionsForRegistration: List<SubstitutionViewRecord>,
    ): App<Nothing, List<ParticipantForExecutionDto>> =
        KIO.comprehension {
            val orderedSubs = substitutionsForRegistration.sortedBy { it.orderForRound }

            data class PersistedNamedParticipant(
                val id: UUID,
                val name: String,
            )

            val participantsStillInToRole = teamParticipants.map { p ->
                val subsRelevantForParticipant = orderedSubs.filter { sub ->
                    sub.participantIn!!.id == p.id || sub.participantOut!!.id == p.id
                }
                p to subsRelevantForParticipant
            }.filter { (participant, subs) ->
                if (subs.isEmpty()) {
                    true
                } else {
                    subs.last().participantIn!!.id == participant.id || (SubstitutionService.getSwapSubstitution(
                        subs.last(),
                        subs
                    ) != null)
                }
            }.map { (participant, subs) ->
                participant to if (subs.isEmpty()) {
                    PersistedNamedParticipant(
                        id = participant.namedParticipantId,
                        name = participant.namedParticipantName,
                    )
                } else {
                    val subNamedParticipant = if (subs.last().participantIn!!.id == participant.id) {
                        subs.last() // This is the scenario if the last sub is a sub in - it doesn't matter if that is a sub in or a swap
                    } else {
                        subs[subs.lastIndex - 1] // As checked before this is the scenario where the last sub was a swap so the namedParticipant comes from the swap substitution (second to last)
                    }
                    PersistedNamedParticipant(
                        id = subNamedParticipant.namedParticipantId!!,
                        name = subNamedParticipant.namedParticipantName!!,
                    )
                }
            }

            val subbedInParticipants = orderedSubs
                .asReversed()
                .distinctBy { it.participantIn!!.id }
                .asReversed()
                .filter { sub ->
                    val participantInId = sub.participantIn!!.id
                    // Filter subIns by participantsStillInToRole
                    if (participantsStillInToRole.none { it.first.id == participantInId }) {
                        val substitutionsRelevantForSubIn = orderedSubs.filter {
                            participantInId == it.participantOut!!.id || participantInId == it.participantIn!!.id
                        }
                        if (substitutionsRelevantForSubIn.isNotEmpty()) {
                            // If the last sub was sub.participantIn being subbed in - add it to subbedInParticipants
                            substitutionsRelevantForSubIn.last().participantIn!!.id == participantInId
                        } else {
                            false
                        }
                    } else false
                }.map {
                    !it.toParticipantForExecutionDto(it.participantIn!!)
                }


            // NamedParticipant comes from the substitution (p.second) so it cant be mapped via the normal conversion
            val mappedParticipantsStillIn = participantsStillInToRole.map { p ->
                ParticipantForExecutionDto(
                    id = p.first.id,
                    namedParticipantId = p.second.id,
                    namedParticipantName = p.second.name,
                    firstName = p.first.firstName,
                    lastName = p.first.lastName,
                    year = p.first.year,
                    gender = p.first.gender,
                    clubId = p.first.clubId,
                    clubName = p.first.clubName,
                    competitionRegistrationId = p.first.competitionRegistrationId,
                    competitionRegistrationName = p.first.competitionRegistrationName,
                    external = p.first.external,
                    externalClubName = p.first.externalClubName,
                )
            }

            KIO.ok(mappedParticipantsStillIn + subbedInParticipants)
        }


    fun getCurrentRoundId(competitionId: UUID): App<ServiceError, UUID?> = KIO.comprehension {
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        KIO.ok(getCurrentAndNextRound(setupRounds).first?.setupRoundId)
    }

    fun getStartList(
        matchId: UUID,
        startListType: StartListFileType,
        startTimeRequired: Boolean
    ): App<ServiceError, File> = KIO.comprehension {
        val match = !CompetitionMatchRepo.getForStartList(matchId).orDie()
            .onNullFail { CompetitionExecutionError.MatchNotFound }
            .failIf({ it.teams!!.isEmpty() }) { CompetitionExecutionError.MatchTeamNotFound }
            .failIf({ startTimeRequired && it.startTime == null }) { CompetitionExecutionError.StartTimeNotSet }

        val data = !CompetitionMatchData.fromPersisted(match)

        val (bytes, extension) = when (startListType) {
            StartListFileType.PDF -> {
                val pdfTemplate = !DocumentTemplateRepo.getAssigned(DocumentType.START_LIST, match.event!!).orDie()
                    .andThenNotNull { it.toPdfTemplate() }
                buildPdf(data, pdfTemplate) to "pdf"
            }

            is StartListFileType.CSV -> {
                val config = !StartListConfigRepo.get(startListType.config).orDie()
                    .onNullFail { StartListConfigError.NotFound }
                buildCsv(data, config) to "csv"
            }
        }

        val fileNameDate = if (startTimeRequired) {
            data.startTime!!.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "-"
        } else {
            ""
        }

        KIO.ok(
            File(
                name = "${fileNameDate}startList-${data.competition.identifier}-${data.roundName}-${data.order}${data.matchName?.let { "-$it" } ?: ""}.$extension",
                bytes = bytes,
            )
        )
    }

    fun downloadStartlist(
        eventId: UUID,
        matchId: UUID,
        type: StartListFileType,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val startListFile = !getStartList(matchId, type, startTimeRequired = true)

        KIO.ok(
            ApiResponse.File(name = startListFile.name, bytes = startListFile.bytes)
        )
    }

    fun getCompetitionPlaceCSV(
        competitionId: UUID,
    ): App<ServiceError, File> = KIO.comprehension {

        val teamsData = !computeCompetitionPlaces(competitionId)
        val competitionData = !CompetitionRepo.getDataForCsvResultsByCompetitionId(competitionId).orDie().onNullFail { CompetitionError.CompetitionNotFound }

        val bytes = buildCompetitionPlacesCsv(teamsData, competitionData.toDto())

        val fileNameDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + "-"

        KIO.ok(
            File(
                name = "${fileNameDate}CompetitionPlaces-${competitionData.competitionName}.csv",
                bytes = bytes,
            )
        )
    }

    fun downloadCompetitionPlacesCSV(
        eventId: UUID,
        competitionId: UUID
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val competitionPacesCSV = !getCompetitionPlaceCSV(competitionId)

        KIO.ok(
            ApiResponse.File(name = competitionPacesCSV.name, bytes = competitionPacesCSV.bytes)
        )
    }

    fun buildCompetitionPlacesCsv(
        teamsData:  List<Pair<CompetitionMatchTeamWithRegistration, Int>>,
        competitionData: EventDataForCompetitionResultsData
    ): ByteArray {

        val bytes = ByteArrayOutputStream().use { out ->
            CSV.write(
                out,
                teamsData.sortedBy { it.second }
            ) {

                column("Veranstaltung") { competitionData.eventName }
                competitionData.eventDateRange?.let {
                    column("Veranstaltungsstart") { competitionData.eventDateRange.first.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                    column("Veranstaltungsende") { competitionData.eventDateRange.second.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                }
                column("Wettkampf") { competitionData.competitionName }
                column("Platz") { second.toString()}
                column("Team") { singletonOrFallback(first.participants.map { it.externalClubName }.toSet(), first.mixedTeamTerm)?: first.clubName }
                column("Anmelder") { first.clubName + if (first.teamNumber != null) " | ${first.teamNumber}" else "" }
                column("Teammitglieder"){ first.participants.joinToString(", ") { "${it.firstName} ${it.lastName} [${it.namedParticipantName}] (${it.externalClubName?:first.clubName})" }}

            }
            out.toByteArray()
        }

        return bytes
    }

    fun buildPdf(
        data: CompetitionMatchData,
        template: PageTemplate?,
    ): ByteArray {
        val doc = document(template) {
            page {
                block(
                    padding = Padding(bottom = 25f),
                ) {
                    text(
                        fontStyle = FontStyle.BOLD,
                        fontSize = 14f,
                    ) {
                        "Wettkampf / "
                    }
                    text(
                        fontSize = 12f,
                        newLine = false,
                    ) {
                        "Competition"
                    }

                    table(
                        padding = Padding(5f, 10f, 0f, 0f)
                    ) {
                        column(0.1f)
                        column(0.25f)
                        column(0.65f)

                        row {
                            cell {
                                text(
                                    fontSize = 12f,
                                ) { data.competition.identifier }
                            }
                            cell {
                                data.competition.shortName?.let {
                                    text(
                                        fontSize = 12f,
                                    ) { it }
                                }
                            }
                            cell {
                                text(
                                    fontSize = 12f,
                                ) { data.competition.name }
                            }
                        }
                    }

                    if (data.startTime != null) {
                        block(
                            padding = Padding(top = 10f, left = 10f),
                        ) {
                            text(
                                fontStyle = FontStyle.BOLD,
                                fontSize = 11f,
                            ) {
                                "Startzeit / "
                            }
                            text(
                                fontSize = 9f,
                                newLine = false,
                            ) {
                                "Start time"
                            }
                            text(
                                newLine = false,
                            ) { "  ${data.startTime.hr()}" }
                            if (data.startTimeOffset != null) {
                                text(
                                    newLine = false,
                                ) { " (versetzte Starts)" }
                            }
                        }
                    }

                }

                var startingIndex = 0
                data.teams.sortedBy { it.startNumber }.forEach { team ->
                    block(
                        padding = Padding(0f, 0f, 0f, 25f)
                    ) {

                        block(
                            padding = Padding(bottom = 5f),
                        ) {
                            text(
                                fontStyle = FontStyle.BOLD,
                                fontSize = 11f,
                            ) {
                                "Startnummer / "
                            }
                            text(
                                fontSize = 9f,
                                newLine = false,
                            ) {
                                "Start number"
                            }

                            text(
                                newLine = false,
                                fontStyle = FontStyle.BOLD,
                                fontSize = 12f,
                            ) { "  ${team.startNumber}" }

                            if (team.deregistered) {
                                text(
                                    newLine = false,
                                ) { "    ABGEMELDET" }
                            }
                        }

                        block(
                            padding = Padding(left = 5f),
                        ) {
                            text(
                                fontStyle = FontStyle.BOLD
                            ) { team.actualClubName ?: team.registeringClubName }
                            block(
                                padding = Padding(left = 5f),
                            ) {
                                text(
                                    fontStyle = FontStyle.BOLD,
                                    fontSize = 8f,
                                ) {
                                    "gemeldet von / "
                                }
                                text(
                                    newLine = false,
                                    fontSize = 8f,
                                ) {
                                    "registered by" + "   ${team.registeringClubName}${if (team.teamName != null) " | ${team.teamName}" else ""}"
                                }
                            }
                            team.ratingCategory?.let {
                                text(
                                    newLine = false,
                                ) { " ${it.name}" }
                            }
                            if (data.startTimeOffset != null && !team.deregistered) {
                                if (data.startTime != null) {
                                    text {
                                        "startet ${
                                            data.startTime.plusSeconds(data.startTimeOffset * startingIndex)
                                                .hrTime()
                                        }"
                                    }
                                } else if (startingIndex != 0) {
                                    text {
                                        "startet versetzt um ${data.startTimeOffset * startingIndex} Sekunden"
                                    }
                                }
                                startingIndex += 1
                            }
                        }

                        table(
                            padding = Padding(5f, 0f, 0f, 0f),
                            withBorder = true,
                        ) {
                            column(0.15f)
                            column(0.05f)
                            column(0.2f)
                            column(0.2f)
                            column(0.1f)
                            column(0.3f)

                            team.participants
                                .sortedBy { it.role }
                                .forEachIndexed { idx, member ->
                                    row(
                                        color = if (idx % 2 == 1) Color(230, 230, 230) else null,
                                    ) {
                                        cell {
                                            text { member.role }
                                        }
                                        cell {
                                            text { member.gender.name }
                                        }
                                        cell {
                                            text { member.firstname }
                                        }
                                        cell {
                                            text { member.lastname }
                                        }
                                        cell {
                                            text { member.year.toString() }
                                        }
                                        cell {
                                            text { member.externalClubName ?: team.registeringClubName }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }

        val bytes = ByteArrayOutputStream().use {
            doc.save(it)
            doc.close()
            it.toByteArray()
        }

        return bytes
    }

    private fun Gender.order() = when (this) {
        Gender.M -> 2
        Gender.F -> 1
        Gender.D -> 3
    }

    fun buildCsv(
        data: CompetitionMatchData,
        config: StartlistExportConfigRecord,
    ): ByteArray {

        val bytes = ByteArrayOutputStream().use { out ->
            CSV.write(
                out,
                data.teams.sortedBy { it.startNumber },
                writeHeader = config.noHeader != true,
            ) {
                // Columns carrying the stable team identifier. They are the source of truth for
                // re-matching results on import, independent of the (externally editable) start number.
                // The configured header must map to a pass-through field of the timing tooling (e.g.
                // Webscorer's "Info 1", RaceClocker's "Extra info") so it survives timing into the
                // results export.
                //
                // Which of the two applies depends on that tooling: the registration id is stable across
                // rounds, the match team id is unique per team and round. Tooling that holds every round
                // of a competition in one race needs the latter to tell the rounds apart.
                optionalColumn(config.colTeamRegistrationId) { registrationId.toString() }
                optionalColumn(config.colTeamMatchId) { matchTeamId.toString() }

                optionalColumn(config.colParticipantFirstname) { participants.joinToString(",") { p -> p.firstname } }
                optionalColumn(config.colParticipantLastname) { participants.joinToString(",") { p -> p.lastname } }
                // Combined name for tooling that offers only a single name field (e.g. RaceClocker).
                optionalColumn(config.colParticipantFullname) {
                    participants.joinToString(", ") { p -> "${p.firstname} ${p.lastname}" }
                }
                optionalColumn(config.colParticipantGender) {
                    participants.map { p -> p.gender }.toSortedSet { a, b -> compareValues(a.order(), b.order()) }
                        .joinToString("/")
                }
                optionalColumn(config.colParticipantYear) { participants.joinToString(",") { p -> p.year.toString() } }
                optionalColumn(config.colParticipantRole) { participants.map { p -> p.role }.toSet().joinToString(",") }
                optionalColumn(config.colParticipantClub) {
                    participants.map {
                        it.externalClubName ?: registeringClubName
                    }.toSet().joinToString(",")
                }

                optionalColumn(config.colClubName) { registeringClubName }

                optionalColumn(config.colTeamName) { teamName ?: "" }
                optionalColumn(config.colTeamStartNumber) { startNumber.toString() }
                optionalColumn(config.colTeamRatingCategory) { ratingCategory?.name ?: "" }
                optionalColumn(config.colTeamClub) { actualClubName ?: registeringClubName }

                optionalColumn(config.colMatchName) { data.matchName ?: "" }
                optionalColumn(config.colMatchStartTime) { idx ->
                    val offsetSeconds = idx * (data.startTimeOffset ?: 0)
                    // TODO: make this configurable
                    LocalTime.ofSecondOfDay(offsetSeconds)
                        //data.startTime.toLocalTime().plusSeconds(offsetSeconds)
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                }

                optionalColumn(config.colRoundName) { data.roundName }

                optionalColumn(config.colCompetitionIdentifier) { data.competition.identifier }
                optionalColumn(config.colCompetitionName) { data.competition.name }
                // Tooling that groups results by a single field (RaceClocker) cannot combine two columns
                // itself, so the rating category is folded into this one on request. Without a rating
                // category the short name stays alone, rather than trailing a separator.
                optionalColumn(config.colCompetitionShortName) {
                    val shortName = data.competition.shortName ?: ""
                    val rating = ratingCategory?.name?.takeIf { config.appendRatingToShortName == true }
                    if (rating != null && shortName.isNotBlank()) {
                        "$shortName $RATING_SEPARATOR $rating"
                    } else {
                        rating ?: shortName
                    }
                }
                optionalColumn(config.colCompetitionCategory) { data.competition.category ?: "" }

                config.colTeamDeregistered?.let {
                    overrideColumn(
                        header = it,
                        cellCondition = { deregistered },
                    ) { if (deregistered) config.valueTeamDeregistered ?: "X" else "" }
                }
            }

            out.toByteArray()
        }

        return bytes
    }

    fun downloadTeamResultDocument(
        documentId: UUID,
        clubId: UUID?,
        scope: Privilege.Scope
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val document = !CompetitionMatchTeamDocumentDataRepo.getDownload(documentId).orDie()
            .onNullFail { CompetitionExecutionError.ResultDocumentNotFound }

        !KIO.failOn(scope == Privilege.Scope.OWN && clubId != document.club) {
            AuthError.PrivilegeMissing
        }

        KIO.ok(
            ApiResponse.File(
                name = document.name!!,
                bytes = document.data!!,
            )
        )
    }

    fun downloadTeamResultDocument(
        documentId: UUID,
        accessToken: String
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {

        val document = !CompetitionMatchTeamDocumentDataRepo.getDownload(documentId).orDie()
            .onNullFail { CompetitionExecutionError.ResultDocumentNotFound }

        val eventParticipant =
            !EventParticipantRepo.getByToken(accessToken).orDie().onNullFail { EventParticipantError.TokenNotFound }

        val participant = !ParticipantRepo.get(eventParticipant.participant).orDie().onNullDie("Referenced entity")

        !KIO.failOn(participant.club != document.club) {
            AuthError.PrivilegeMissing
        }

        // TODO: @Improve validation, only okay, if document is from team including this participant

        KIO.ok(
            ApiResponse.File(
                name = document.name!!,
                bytes = document.data!!,
            )
        )

    }

    fun getRoundExistingForCompetition(competitionId: UUID) =
        COMPETITION_MATCH_FOR_EVENT.exists { COMPETITION_ID.eq(competitionId) }
}