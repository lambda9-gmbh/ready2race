package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionDeregistration.control.CompetitionDeregistrationRepo
import de.lambda9.ready2race.backend.app.competitionDeregistration.entity.CompetitionDeregistrationError.IsLocked
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionRoundDto
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionTeamPlaceDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.*
import de.lambda9.ready2race.backend.app.competitionMatchTeam.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.control.*
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.documentTemplate.control.DocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toPdfTemplate
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.matchResultImportConfig.control.MatchResultImportConfigRepo
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigError
import de.lambda9.ready2race.backend.app.startListConfig.control.StartListConfigRepo
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigError
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.control.applyNewRound
import de.lambda9.ready2race.backend.app.substitution.control.toParticipantForExecutionDto
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.calls.requests.FileUpload
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.noDataResponse
import de.lambda9.ready2race.backend.csv.CSV
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.hr
import de.lambda9.ready2race.backend.hrTime
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validators.CollectionValidators.noDuplicates
import de.lambda9.ready2race.backend.xls.CellParser.Companion.int
import de.lambda9.ready2race.backend.xls.CellParser.Companion.maybe
import de.lambda9.ready2race.backend.xls.CellParser.Companion.string
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

object CompetitionExecutionService {

    fun getMatchesByEvent(
        eventId: UUID,
        currentlyRunning: Boolean? = null,
        withoutPlaces: Boolean? = null
    ): App<ServiceError, ApiResponse.ListDto<MatchForRunningStatusDto>> = KIO.comprehension {
        val matches = !CompetitionMatchRepo.getMatchesByEvent(eventId, currentlyRunning, withoutPlaces).orDie()
        KIO.ok(ApiResponse.ListDto(matches))
    }

    fun createNewRound(
        competitionId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        var createFollowingRound = true
        while (createFollowingRound) {
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

            val (currentRound, nextRound) = getCurrentAndNextRound(setupRounds)

            if (currentRound == null) {
                // First Round

                val registrations = !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()

                !checkRoundCreation(true, setupRounds, null, nextRound, registrations)

                val nextRoundSetupMatches =
                    nextRound!!.setupMatches.sortedBy { it.weighting }

                val sortedRegistrations = registrations
                    .filter { it.teamNumber != null } // Registrations without teamNumber are ignored. A confirmation Dialog makes aware of this behaviour
                    .sortedBy { it.teamNumber }


                val matchRecords = nextRoundSetupMatches
                    .filterIndexed { index, _ -> index < sortedRegistrations.size }
                    .map {
                        !it.applyCompetitionMatch(userId, null)
                    }
                !CompetitionMatchRepo.create(matchRecords).orDie()


                val seedingList = getSeedingList(nextRoundSetupMatches.map { it.teams }, registrations.size)


                val newTeamRecords = sortedRegistrations.mapIndexed { index, reg ->
                    val matchIndex = seedingList.indexOfFirst { it.contains(index + 1) }

                    val automaticFirstPlace = !nextRound.required && seedingList[matchIndex].filter { it <= registrations.size }.size == 1

                    CompetitionMatchTeamRecord(
                        id = UUID.randomUUID(),
                        competitionMatch = matchRecords[matchIndex].competitionSetupMatch,
                        competitionRegistration = reg.id,
                        startNumber = seedingList[matchIndex].indexOfFirst { it == index + 1 } + 1,
                        place = if (automaticFirstPlace) 1 else null,
                        out = false,
                        failed = false,
                        failedReason = null,
                        createdAt = LocalDateTime.now(),
                        createdBy = userId,
                        updatedAt = LocalDateTime.now(),
                        updatedBy = userId,
                    )
                }
                !CompetitionMatchTeamRepo.create(newTeamRecords).orDie()

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
        }

        noData
    }

    fun getProgress(
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

            val lastRoundFinished =
                if (currentAndNextRound.second == null && currentAndNextRound.first != null) {
                    currentAndNextRound.first!!.matches.flatMap { match -> match.teams.filter { it.place == null } }
                        .isEmpty()
                } else false

            val sortedRounds = sortRounds(setupRounds)

            sortedRounds.filter { it.matches.isNotEmpty() }.traverse { round ->
                round.copy(matches = round.matches.map { match -> match.copy(teams = match.teams.filter { !it.out }) }).toCompetitionRoundDto()
            }.map {
                ApiResponse.Dto(
                    CompetitionExecutionProgressDto(
                        rounds = it,
                        canNotCreateRoundReasons,
                        lastRoundFinished
                    )
                )
            }
        }

    private fun sortRounds(
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

            // TODO: @Evaluate: is this not implicitly checked by the following check?
            val currentRoundPlaces =
                currentRound.matches.flatMap { match ->
                    match.teams.filter { !it.deregistered && !it.failed && !it.out }.map { team -> team.place }
                }

            val placesAreMissing = currentRound.matches.map { match ->
                match.teams.map { it.place }.containsAll((1..match.teams.filter { !it.deregistered && !it.failed && !it.out }.size).toList())
            }.any { !it }


            if (currentRoundPlaces.contains(null) || placesAreMissing)
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
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchRequest
    ): App<CompetitionExecutionError, ApiResponse.NoData> = KIO.comprehension {


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
        val outStartNumbers = outTeams.sortedBy { it.startNumber }.mapIndexed { index, team -> team.id to index + highestStartNumber + 1 }.toMap()

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
    ): App<ServiceError, CompetitionMatchWithTeams> = KIO.comprehension {

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        !KIO.failOn(setupRounds.flatMap { it.setupMatches.toList() }
            .find { it.id == matchId } == null) { CompetitionExecutionError.MatchNotFound }

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)

        val match = currentRound.matches.find { it.competitionSetupMatch == matchId }
            ?: return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)

        !KIO.failOn(!currentRound.required && match.teams.size == 1) { CompetitionExecutionError.MatchResultsLocked }


        KIO.ok(match)
    }

    private fun prepareForNewPlaces(
        matchId: UUID,
        userId: UUID,
    ): App<Nothing, Unit> = KIO.comprehension {

        !CompetitionMatchRepo.update(matchId) {
            currentlyRunning = false
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        !CompetitionMatchTeamRepo.updateManyByMatch(matchId) {
            place = null
        }.orDie()

        unit
    }

    fun updateMatchResult(
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchResultRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        !checkUpdateMatchResult(competitionId, matchId)
        !prepareForNewPlaces(matchId, userId)

        // TODO: validate places continuous

        request.teamResults.traverse { result ->
            CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, result.registrationId) {
                this.place = result.place
                this.failed = result.failed
                this.failedReason = result.failedReason
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
        }.noDataResponse()
    }

    fun updateMatchResultByFile(
        competitionId: UUID,
        matchId: UUID,
        file: FileUpload,
        request: UploadMatchResultRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val match = !checkUpdateMatchResult(competitionId, matchId)
        !prepareForNewPlaces(matchId, userId)

        val config = !MatchResultImportConfigRepo.get(request.config).orDie().onNullFail { MatchResultImportConfigError.NotFound }

        val iStream = file.bytes.inputStream()

        val teams = !XLS.read(iStream) {
            ParsedTeamResult(
                startNumber = !cell(config.colTeamStartNumber, int),
                place = !optionalCell(config.colTeamPlace, maybe(int)),
                noResultReason = !optionalCell(config.colTeamPlace, maybe(string))
            )
        }.mapError {
            when (it) {
                is XLSReadError.CellError.ColumnUnknown -> CompetitionExecutionError.ResultUploadError.ColumnUnknown(it.expected)
                is XLSReadError.CellError.ParseError.CellBlank -> CompetitionExecutionError.ResultUploadError.CellBlank(it.row, it.col)
                is XLSReadError.CellError.ParseError.WrongCellType -> CompetitionExecutionError.ResultUploadError.WrongCellType(it.row, it. col, it.actual.name, it.expected.name)
                is XLSReadError.CellError.ParseError.UnparsableStringValue -> CompetitionExecutionError.ResultUploadError.UnparsableString(it.row, it.col, it.value)
                XLSReadError.FileError -> CompetitionExecutionError.ResultUploadError.FileError
                XLSReadError.NoHeaders -> CompetitionExecutionError.ResultUploadError.NoHeaders
            }
        }

        !noDuplicates(teams.map { it.startNumber }).fold(
            onValid = { unit },
            onInvalid = { when (it) {
                is ValidationResult.Invalid.Duplicates -> KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.DuplicatedStartNumbers(it))
                else -> KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.Unexpected(it))
            } }
        )

        val places = teams.map { it.place }

        !noDuplicates(places).fold(
            onValid = { unit },
            onInvalid = { when (it) {
                is ValidationResult.Invalid.Duplicates -> KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.DuplicatedPlaces(it))
                else -> KIO.fail(CompetitionExecutionError.ResultUploadError.Invalid.Unexpected(it))
            }}
        )

        // TODO: disabled for now, because it helps with parallel matches (can upload results to multiple matches with the same file)
        //!KIO.failOn(teams.size != match.teams.size) { CompetitionExecutionError.ResultUploadError.WrongTeamCount(teams.size, match.teams.size) }

        // TODO: disabled for now, because it forbids upload of same results for parallel races
        /*places.filterNotNull().sorted().forEachIndexed { index, place ->
            val expected = index + 1
            !KIO.failOn(expected != place) { CompetitionExecutionError.ResultUploadError.Invalid.PlacesUncontinuous(place, expected) }
        }*/
        // TODO: instead for now, we sort the places and give first place to smallest place in expected start numbers maintaining teams with place == null
        val validTeams = teams.filter { team -> match.teams.any {team.startNumber == it.startNumber && !it.deregistered} }
        val (teamWithoutPlace, teamWithPlace) = validTeams.partition { it.place == null }
        val correctedTeams = teamWithoutPlace + teamWithPlace.sortedBy { it.place!! }.mapIndexed { idx, res -> res.copy(place = idx + 1) }

        !correctedTeams.traverse { result ->

            KIO.comprehension {

                // TODO: better error for frontend
                val registrationId = !KIO.failOnNull(match.teams.find { it.startNumber == result.startNumber }?.competitionRegistration) { CompetitionExecutionError.MatchTeamNotFound }

                CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, registrationId) {
                    this.place = result.place
                    this.failed = result.place == null
                    this.failedReason = result.noResultReason
                    updatedBy = userId
                    updatedAt = LocalDateTime.now()
                }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
            }
        }

        noData

    }

    fun updateMatchRunningState(
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchRunningStateRequest
    ): App<CompetitionExecutionError, ApiResponse.NoData> = KIO.comprehension {

        !CompetitionMatchRepo.exists(matchId).orDie().onNullFail { CompetitionExecutionError.MatchNotFound }

        !CompetitionMatchRepo.update(matchId) {
            currentlyRunning = request.currentlyRunning
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        noData
    }


    fun deleteCurrentRound(
        competitionId: UUID
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)

        !SubstitutionRepo.deleteBySetupRoundId(currentRound.setupRoundId).orDie()

        val deleted = !CompetitionMatchRepo.delete(currentRound.matches.map { it.competitionSetupMatch }).orDie()

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
        val highestDefinedTeamCount = teams.maxBy { it ?: 0 } ?: 0

        val teamsForEachUndefinedTeams = if (teams.any { it == null }) {
            maxTeamsNeeded - teams.filterNotNull().sum() / teams.filter { it == null }.size
        } else 0

        return if (highestDefinedTeamCount > teamsForEachUndefinedTeams) {
            highestDefinedTeamCount
        } else {
            teamsForEachUndefinedTeams
        }
    }

    fun getCompetitionPlaces(
        eventId: UUID,
        competitionId: UUID,
        scope: Privilege.Scope?,
    ): App<ServiceError, ApiResponse.ListDto<CompetitionTeamPlaceDto>> = KIO.comprehension {

        !EventRepo.getScoped(eventId, scope).orDie().onNullFail { EventError.NotFound }

        val setupRoundRecords = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val setupRounds = sortRounds(setupRoundRecords)


        val roundsWithTeamsToPlaces =
            setupRounds.filterIndexed { roundIdx, round -> // filters out rounds for which there was no following round created yet
                if (roundIdx < setupRounds.size - 1) {
                    setupRounds[roundIdx + 1].matches.isNotEmpty()
                } else round.matches.flatMap { it.teams }.none { it.place == null && !it.deregistered && !it.out && !it.failed }
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


        val result = !roundsWithTeamsToPlaces
            .flatten()
            .sortedBy { it.second }
            .traverse { it.first.toCompetitionTeamPlaceDto(it.second) }

        KIO.ok(ApiResponse.ListDto(result))
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
            }.filter { pToSubs ->
                if (pToSubs.second.isEmpty()) {
                    true
                } else {
                    pToSubs.second.last().participantIn!!.id == pToSubs.first.id
                }
            }.map { pToSubs ->
                pToSubs.first to if (pToSubs.second.isEmpty()) {
                    PersistedNamedParticipant(
                        id = pToSubs.first.namedParticipantId,
                        name = pToSubs.first.namedParticipantName,
                    )
                } else {
                    PersistedNamedParticipant(
                        id = pToSubs.second.last().namedParticipantId!!,
                        name = pToSubs.second.last().namedParticipantName!!,
                    )
                }
            }

            val subbedInParticipants = orderedSubs
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

            // todo: clean up
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


    fun downloadStartlist(
        matchId: UUID,
        type: StartListFileType,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {

        val match = !CompetitionMatchRepo.getForStartList(matchId).orDie()
            .onNullFail { CompetitionExecutionError.MatchNotFound }
            .failIf({ it.teams!!.isEmpty() }) { CompetitionExecutionError.MatchTeamNotFound }
            .failIf({ it.startTime == null }) { CompetitionExecutionError.StartTimeNotSet }

        val data = !CompetitionMatchData.fromPersisted(match)

        val (bytes, extension) = when (type) {
            StartListFileType.PDF -> {
                val pdfTemplate = !DocumentTemplateRepo.getAssigned(DocumentType.START_LIST, match.event!!).orDie()
                    .andThenNotNull { it.toPdfTemplate() }
                buildPdf(data, pdfTemplate) to "pdf"
            }

            is StartListFileType.CSV -> {
                val config = !StartListConfigRepo.get(type.config).orDie()
                    .onNullFail { StartListConfigError.NotFound }
                buildCsv(data, config) to "csv"
            }
        }

        KIO.ok(
            ApiResponse.File(
                name = "${data.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}-startList-${data.competition.identifier}-${data.roundName}-${data.order}${data.matchName?.let { "-$it" } ?: ""}.$extension",
                bytes = bytes,
            )
        )
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
                            ) { team.registeringClubName }
                            team.teamName?.let {
                                text(
                                    newLine = false,
                                ) { " $it" }
                            }
                            team.actualClubName?.let {
                                text(
                                    newLine = false,
                                ) { " [$it]" }
                            }
                            team.ratingCategory?.let {
                                text(
                                    newLine = false,
                                ) { " ${it.name}" }
                            }
                            if (data.startTimeOffset != null && !team.deregistered) {
                                text {
                                    "startet ${
                                        data.startTime.plusSeconds(data.startTimeOffset * startingIndex)
                                            .hrTime()
                                    }"
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
                data.teams.sortedBy { it.startNumber }
            ) {
                optionalColumn(config.colParticipantFirstname) { participants.joinToString(",") { p -> p.firstname } }
                optionalColumn(config.colParticipantLastname) { participants.joinToString(",") { p -> p.lastname } }
                optionalColumn(config.colParticipantGender) { participants.map { p -> p.gender }.toSortedSet { a ,b -> compareValues(a.order(), b.order()) }.joinToString("/") }
                optionalColumn(config.colParticipantYear) { participants.joinToString(",") { p -> p.year.toString() } }
                optionalColumn(config.colParticipantRole) { participants.map { p -> p.role }.toSet().joinToString(",") }
                optionalColumn(config.colParticipantClub) { participants.map { it.externalClubName ?: registeringClubName }.toSet().joinToString(",") }

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
                optionalColumn(config.colCompetitionShortName) { data.competition.shortName ?: "" }
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
}