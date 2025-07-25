package de.lambda9.ready2race.backend.app.competitionExecution.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionRoundDto
import de.lambda9.ready2race.backend.app.competitionExecution.control.toCompetitionTeamPlaceDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.*
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupParticipantRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupRoundRepo
import de.lambda9.ready2race.backend.app.competitionSetup.control.applyCompetitionMatch
import de.lambda9.ready2race.backend.app.competitionMatchTeam.control.CompetitionMatchTeamRepo
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupMatchRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupPlacesOption
import de.lambda9.ready2race.backend.app.documentTemplate.control.DocumentTemplateRepo
import de.lambda9.ready2race.backend.app.documentTemplate.control.toPdfTemplate
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentType
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.control.applyNewRound
import de.lambda9.ready2race.backend.app.substitution.control.toParticipantForExecutionDto
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.csv.CSV
import de.lambda9.ready2race.backend.database.generated.tables.records.*
import de.lambda9.ready2race.backend.hr
import de.lambda9.ready2race.backend.hrTime
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.*
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

object CompetitionExecutionService {

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

                    val automaticFirstPlace = seedingList[matchIndex].filter { it <= registrations.size }.size == 1

                    CompetitionMatchTeamRecord(
                        id = UUID.randomUUID(),
                        competitionMatch = matchRecords[matchIndex].competitionSetupMatch,
                        competitionRegistration = reg.id,
                        startNumber = seedingList[matchIndex].indexOfFirst { it == index + 1 } + 1,
                        place = if (automaticFirstPlace) 1 else null,
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
                        match.second!!.teams.sortedBy { team -> team.place }.mapIndexed { teamIdx, team ->
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
                        currentTeamsToParticipantId.filter {
                            it.second!!.competitionSetupMatch == nextRoundTeam.competitionSetupMatch
                        }.size == 1

                    CompetitionMatchTeamRecord(
                        id = UUID.randomUUID(),
                        competitionMatch = nextRoundTeam.competitionSetupMatch!!,
                        competitionRegistration = prevTeam.competitionRegistration,
                        startNumber = nextRoundTeam.ranking,
                        place = if (automaticFirstPlace) 1 else null,
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


                if (newTeamRecords.size > nextRoundSetupMatches.size || nextRound.required || nextRound.nextRound == null
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

            val registrationsIfFirst = if (currentAndNextRound.first == null) {
                !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()
            } else {
                null
            }

            val canNotCreateRoundReasons = !checkRoundCreation(
                false,
                setupRounds,
                currentAndNextRound.first,
                currentAndNextRound.second,
                registrationsIfFirst,
            )

            val lastRoundFinished =
                if (currentAndNextRound.second == null && currentAndNextRound.first != null) {
                    currentAndNextRound.first!!.matches.flatMap { match -> match.teams.filter { it.place == null } }
                        .isEmpty()
                } else false

            val sortedRounds = sortRounds(setupRounds)

            sortedRounds.filter { it.matches.isNotEmpty() }.traverse { round ->
                round.toCompetitionRoundDto()
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
            val currentRoundPlaces =
                currentRound.matches.flatMap { match -> match.teams.map { team -> team.place } }

            if (currentRoundPlaces.contains(null))
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

        if (!setupRound.required && teamRecords.size == 1) {
            return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)
        }

        !CompetitionMatchRepo.update(matchId) {
            startTime = request.startTime
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        if (teamRecords.size != request.teams.size) {
            return@comprehension KIO.fail(CompetitionExecutionError.TeamsNotMatching)
        }
        teamRecords.forEach { tr ->
            if (request.teams.filter { it.registrationId == tr.competitionRegistration }.size != 1)
                return@comprehension KIO.fail(CompetitionExecutionError.TeamsNotMatching)
        }

        !teamRecords.traverse { team ->
            CompetitionMatchTeamRepo.update(team) {
                startNumber = (team.startNumber * -1)
            }.orDie()
        }

        !teamRecords.traverse { team ->
            CompetitionMatchTeamRepo.update(team) {
                startNumber =
                    request.teams.find { it.registrationId == team.competitionRegistration }!!.startNumber // Can be guaranteed by previous checks
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
        }

        noData
    }

    fun updateMatchResult(
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchResultRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        if (setupRounds.flatMap { it.setupMatches.toList() }.find { it.id == matchId } == null) {
            return@comprehension KIO.fail(CompetitionExecutionError.MatchNotFound)
        }

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)

        val match = currentRound.matches.find { it.competitionSetupMatch == matchId }
        if (match == null) {
            return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)
        }

        if (!currentRound.required && match.teams.size == 1) {
            return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)
        }

        request.teamResults.traverse { result ->
            CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, result.registrationId) {
                place = result.place
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
        }.map { ApiResponse.NoData }
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
                } else round.matches.isNotEmpty()
            }.mapIndexed { roundIdx, round ->

                val isLastRound = roundIdx >= setupRounds.size - 1

                val sortedRoundMatches =
                    round.matches.sortedBy { m -> round.setupMatches.first { it.id == m.competitionSetupMatch }.weighting }

                val nonAdvancingTeamsToMatchIndex = sortedRoundMatches.flatMapIndexed { matchIdx, match ->
                    match.teams.map { it to matchIdx }
                }
                    .filter { team -> // Filter teams that will move on to the next round or have no place set in the last round
                        if (!isLastRound) {
                            setupRounds[roundIdx + 1].matches.toList().flatMap { m -> m.teams.toList() }
                                .find { it.competitionRegistration == team.first.competitionRegistration } == null
                        } else team.first.place != null
                    }

                val seedingList =
                    if (round.placesOption != CompetitionSetupPlacesOption.ASCENDING.name || round.placesOption != CompetitionSetupPlacesOption.CUSTOM.name) { // Only relevant if the placesOption is "ascending" or "custom"
                        getSeedingList(
                            currentRoundTeams = sortedRoundMatches
                                .map { it.teams.size },
                            maxTeamsNeeded = setupRounds.getOrNull(roundIdx + 1)?.setupMatches?.sumOf { it.teams ?: 0 }
                                ?: 0)
                    } else null


                val teamsToPlaces = nonAdvancingTeamsToMatchIndex.map { team ->

                    val teamToPlace = when (round.placesOption) {
                        CompetitionSetupPlacesOption.EQUAL.name -> {
                            team.first to if (!isLastRound) {
                                setupRounds[roundIdx + 1].matches.flatMap { m -> m.teams.toList() }.size + 1 // Place is one higher than the count of participants in the next round
                            } else 1 // 1 if this is the final round
                        }

                        CompetitionSetupPlacesOption.ASCENDING.name ->
                            team.first to seedingList!![team.second][team.first.place!! - 1]

                        else ->
                            team.first to round.places.first { it.roundOutcome == seedingList!![team.second][team.first.place!! - 1] }.place
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

    fun downloadStartlist(
        matchId: UUID,
        type: StartListFileType,
    ): App<CompetitionExecutionError, ApiResponse.File> = KIO.comprehension {

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

            StartListFileType.CSV -> buildCsv(data) to "csv"
        }

        KIO.ok(
            ApiResponse.File(
                name = "startList-${data.competition.identifier}-${data.roundName}-${data.order}${data.matchName?.let { "-$it" } ?: ""}.$extension",
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

                data.teams.forEachIndexed { index, team ->
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
                        }

                        block(
                            padding = Padding(left = 5f),
                        ) {
                            text(
                                fontStyle = FontStyle.BOLD
                            ) { team.clubName }
                            team.teamName?.let {
                                text(
                                    newLine = false,
                                ) { " $it" }
                            }
                            if (data.startTimeOffset != null) {
                                text {
                                    "startet ${
                                        data.startTime.plusSeconds((data.startTimeOffset * index).milliseconds.inWholeSeconds)
                                            .hrTime()
                                    }"
                                }
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
                                            text { member.externalClubName ?: team.clubName }
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

    fun buildCsv(
        data: CompetitionMatchData,
    ): ByteArray {

        val bytes = ByteArrayOutputStream().use { out ->
            CSV.write(
                out,
                data.teams
            ) {
                if (data.teams.first().participants.size == 1) {
                    column("First name") { participants.first().firstname }
                    column("Last name") { participants.first().lastname }
                    column("Gender") { participants.first().gender.name }
                } else {
                    column("Name") { participants.joinToString(",") { p -> p.lastname } }
                    column("Gender") { participants.map { p -> p.gender }.toSet().joinToString("/") }
                }
                column("Team name") { clubName }
                column("Team name 2") { teamName ?: "" }
                column("Category") { data.competition.category ?: "" }
                column("Bib") { startNumber.toString() }
                if (data.startTimeOffset != null) {
                    column("Start time") { idx -> (idx * data.startTimeOffset).milliseconds.toIsoString() }
                }
            }

            out.toByteArray()
        }

        return bytes
    }
}