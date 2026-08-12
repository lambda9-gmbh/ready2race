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
import de.lambda9.ready2race.backend.app.eventInfo.boundary.EventChangeMarker
import de.lambda9.ready2race.backend.app.eventParticipant.control.EventParticipantRepo
import de.lambda9.ready2race.backend.app.eventParticipant.entity.EventParticipantError
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleChainService
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.matchResultImportConfig.control.MatchResultImportConfigRepo
import de.lambda9.ready2race.backend.app.matchResultImportConfig.entity.MatchResultImportConfigError
import de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchByeService
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollLogic
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerPollService
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerPollRepo
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerResultsXls
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.calls.comprehension.CallComprehensionScope
import de.lambda9.ready2race.backend.app.startListConfig.control.StartListConfigRepo
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigError
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RankedCategory
import de.lambda9.ready2race.backend.app.ratingcategory.boundary.RatingCategoryRanking
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
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_TRACKING
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
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.collections.sortedBy
import de.lambda9.ready2race.backend.validation.fold

object CompetitionExecutionService {

    private val logger = KotlinLogging.logger {}

    /** Separates competition short name and rating category where both share one export column. */
    private const val RATING_SEPARATOR = "·"

    /**
     * Die Läufe der Veranstaltung, wahlweise gefiltert nach [activated]: true liefert die an den
     * Start gerufenen, false die übrigen. Ob ein aufgerufener Lauf auch schon unterwegs ist, sagt
     * dieser Filter bewusst nicht - dafür gibt es den abgeleiteten Zustand.
     */
    fun getMatchesByEvent(
        eventId: UUID,
        activated: Boolean? = null,
        withoutPlaces: Boolean? = null
    ): App<ServiceError, ApiResponse.ListDto<MatchForRunningStatusDto>> = KIO.comprehension {
        val matches = !CompetitionMatchRepo.getMatchesByEvent(eventId, activated, withoutPlaces).orDie()
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
        // Zeitstrahl: Setup-Match-Ids aller in diesem Aufruf erzeugten Läufe, über alle
        // Runden/Iterationen hinweg — Grundlage für den Slot-Write-Through nach der Schleife.
        val createdSetupMatchIds = mutableListOf<UUID>()
        while (createFollowingRound) {
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

            val (currentRound, nextRound) = getCurrentAndNextRound(setupRounds)

            // Number of teams placed into the round being created - used to resolve the bracket size N below.
            var justPlacedCount = 0

            // Die Setup-Lauf-Ids der Runde, die dieser Durchlauf erzeugt - Grundlage des Vermerks
            // unten. `createdSetupMatchIds` sammelt über alle Durchläufe hinweg und taugt dafür nicht.
            var createdThisRound: List<UUID> = emptyList()

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
                createdSetupMatchIds += matchRecords.map { it.competitionSetupMatch!! }
                createdThisRound = matchRecords.map { it.competitionSetupMatch!! }


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
                createdSetupMatchIds += matchRecords.map { it.competitionSetupMatch!! }
                createdThisRound = matchRecords.map { it.competitionSetupMatch!! }

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

            // Stand die Runde schon einmal, sind diese Paarungen eine Neuberechnung - und die
            // Orga-Ansichten sollen das sehen. Dass es sie schon einmal gab, weiß nur die
            // Setup-Runde: Sie überlebt das Löschen der Runde, die Läufe tun es nicht
            // (siehe V202608091501).
            if (nextRound != null && createdThisRound.isNotEmpty()) {
                val markedAt = LocalDateTime.now()
                if (nextRound.materializedAt != null) {
                    !CompetitionMatchRepo.markPairingsRecalculated(createdThisRound, markedAt).orDie()
                } else {
                    !CompetitionSetupRoundRepo.markMaterialized(nextRound.setupRoundId, markedAt).orDie()
                }
            }
        }

        // Zeitstrahl: geplante Slot-Zeiten auf die soeben erzeugten Läufe stempeln …
        !EventScheduleRepo.stampSlotTimesForSetupMatches(createdSetupMatchIds, userId).orDie()
        // … und die wartende Kette wieder anstoßen: wenn nichts läuft, aktiviert sich der nächste
        // fällige Slot jetzt selbst — das ist der zweite Auslöser des wartenden Breakpoints.
        !ScheduleChainService.resumeIfParked(eventId, userId)

        // Neue Läufe erscheinen im Block „nächste Läufe" der öffentlichen Anzeigen.
        EventChangeMarker.bump(eventId)

        noData
    }

    /**
     * Der Stand der Durchführung: Runden, Läufe, Ergebnisse, Steg-Scans.
     *
     * Antwortet mit ETag, weil die Durchführungsseite diesen Endpunkt im eingestellten Takt abruft
     * (siehe `event.execution_auto_refresh`). Ein unveränderter Stand kommt dann als 304 ohne
     * Rumpf zurück, und die Seite rührt ihren State nicht an - das ist der Unterschied zwischen
     * einem Abgleich, der nebenbei läuft, und einem, der alle fünf Sekunden das Rendern auslöst.
     * Gespart wird die Übertragung, nicht die Abfrage.
     */
    fun getProgress(
        eventId: UUID,
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.ETagged<CompetitionExecutionProgressDto>> =
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

            // Letzter Steg-Scan je Person dieses Wettkampfs — Grundlage des Arena-Chips. Dieselbe
            // Reduktion wie im Schiedsrichter-Dashboard (LiveDashboardService): die Abfrage liefert
            // alle Scans flach, der letzte je Person zählt. Bleibt die Karte leer, läuft die
            // Veranstaltung ohne Check-in und der Chip entfällt (teamsInArena = null).
            val lastScanByParticipant = !CompetitionMatchRepo.getScansByCompetition(eventId, competitionId).orDie()
                .map { scans ->
                    scans.groupBy { it[PARTICIPANT_TRACKING.PARTICIPANT]!! }
                        .mapValues { (_, rows) ->
                            val last = rows.maxBy { it[PARTICIPANT_TRACKING.SCANNED_AT]!! }
                            last[PARTICIPANT_TRACKING.SCAN_TYPE]!! to last[PARTICIPANT_TRACKING.SCANNED_AT]!!
                        }
                }

            // Vor dem out-Filter unten geholt: die abgemeldete Gegnerzeile fällt dort heraus, und
            // genau sie trägt die Ursache des Freiloses.
            val byeByMatch = !MatchByeService.byeByMatch(eventId, competitionId)

            sortedRounds.filter { it.matches.isNotEmpty() }.traverse { round ->
                round.copy(matches = round.matches.map { match -> match.copy(teams = match.teams.filter { !it.out }) })
                    .toCompetitionRoundDto(event.mixedTeamTerm, lastScanByParticipant, byeByMatch)
            }.map {
                ApiResponse.ETagged(
                    CompetitionExecutionProgressDto(
                        rounds = it,
                        canNotCreateRoundReasons,
                        isChallengeEvent = event.challengeEvent!!
                    )
                )
            }
        }

    /**
     * Wie dieser Wettkampf zur Folgerunden-Automatik steht. Der wirksame Wert wird hier gerechnet
     * und nicht im Frontend, damit die Vererbungsregel an genau einer Stelle steht.
     */
    fun getRoundProgressionConfig(
        eventId: UUID,
        competitionId: UUID,
    ): App<ServiceError, ApiResponse.Dto<RoundProgressionConfigDto>> = KIO.comprehension {
        val eventDefault = !EventRepo.getAutoCreateFollowingRounds(eventId).orDie()
        val override = !CompetitionRepo.getAutoCreateFollowingRounds(competitionId).orDie()

        KIO.ok(
            ApiResponse.Dto(
                RoundProgressionConfigDto(
                    autoCreateFollowingRounds = override,
                    eventAutoCreateFollowingRounds = eventDefault,
                    effective = AutoRoundProgressionLogic.effectiveAutoCreate(eventDefault, override),
                )
            )
        )
    }

    fun updateRoundProgressionConfig(
        competitionId: UUID,
        userId: UUID,
        request: RoundProgressionConfigRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !CompetitionRepo.updateAutoCreateFollowingRounds(
            competitionId,
            request.autoCreateFollowingRounds,
            userId,
        ).orDie().onNullFail { CompetitionError.CompetitionNotFound }

        noData
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
            return@comprehension KIO.fail(CompetitionExecutionError.MatchIsBye)
        }

        val slotTime = !EventScheduleRepo.getSlotBySetupMatch(matchId).orDie()
        if (slotTime != null && request.startTime != slotTime) {
            return@comprehension KIO.fail(CompetitionExecutionError.StartTimeManagedBySchedule)
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

        // Bahnentausch und Startzeit stehen auf den öffentlichen Anzeigen.
        EventChangeMarker.bump(eventId)

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
        byeError: ServiceError = CompetitionExecutionError.MatchIsBye,
    ): App<ServiceError, CompetitionMatchWithTeams> = KIO.comprehension {

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        checkUpdateMatchResult(setupRounds, matchId, byeError)
    }

    /**
     * Dieselbe Prüfung mit bereits geholter Turnierstruktur.
     *
     * Für den automatischen Abruf (`RaceClockerPollService`): Er beobachtet mehrere Läufe desselben
     * Wettkampfs gleichzeitig und holt [CompetitionSetupService.getSetupRoundsWithMatches] - zwei
     * Abfragen plus den ganzen Baum aus Runden, Läufen und Mannschaften - deshalb einmal je Takt und
     * Wettkampf statt einmal je Lauf. Die Sperre auf die aktuelle Runde bleibt dabei genau dieselbe
     * wie beim Knopf; sie darf der Job nicht umgehen.
     */
    fun checkUpdateMatchResult(
        setupRounds: List<CompetitionSetupRoundWithMatches>,
        matchId: UUID,
        byeError: ServiceError = CompetitionExecutionError.MatchIsBye,
    ): App<ServiceError, CompetitionMatchWithTeams> = KIO.comprehension {

        !KIO.failOn(setupRounds.flatMap { it.setupMatches.toList() }
            .find { it.id == matchId } == null) { CompetitionExecutionError.MatchNotFound }

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)

        val match = currentRound.matches.find { it.competitionSetupMatch == matchId }
            ?: return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)

        // Ein Freilos mit "muss gefahren werden" nimmt Ergebnisse an wie jeder Lauf - die Zeit
        // wird genommen (und "außer Konkurrenz" gezeigt), nur das Weiterkommen hängt nicht an ihr.
        !KIO.failOn(!currentRound.required && match.teams.size == 1 && !match.byeMustRace) { byeError }


        KIO.ok(match)
    }

    /**
     * Setzt alle Plätze eines Laufs zurück, damit eine neue Ergebnis-Eingabe nicht auf alten
     * Werten aufsetzt.
     *
     * Rührt `activated_at` NICHT MEHR an (C1): egal ob die Ergebnisse aus der manuellen
     * Eingabe, einem Datei-Upload oder dem RaceClocker-Pull vollständig sind, der Lauf bleibt
     * aktiv, bis ein aktives Beenden ihn stempelt (`LiveDashboardService.finishMatch` bzw.
     * `EventScheduleService.finishSlot`). Vorher deaktivierte diese Funktion einen Lauf mit
     * vollständigen Ergebnissen still, ohne `finished_at` zu setzen und ohne die Kette zu ziehen -
     * genau das Loch, das C1 schließt ("Ergebnisse vollständig — wartet auf Beenden").
     */
    private fun prepareForNewPlaces(
        matchId: UUID,
    ): App<Nothing, Unit> = KIO.comprehension {

        !CompetitionMatchTeamRepo.updateManyByMatch(matchId) {
            place = null
        }.orDie()

        unit
    }

    /**
     * Hält den automatischen RaceClocker-Abruf für diesen Lauf an.
     *
     * Wer von Hand einträgt oder eine Datei hochlädt, hat das letzte Wort: Der Job setzt bei jedem
     * Takt alle Plätze des Laufs zurück und schreibt nur die Boote wieder, die im Feed ein Ergebnis
     * haben - ein Handeintrag für ein Boot, das RaceClocker nicht kennt, wäre nach spätestens einem
     * Takt weg. Freigegeben wird der Lauf in der Oberfläche ([resumeRaceClockerAutoPull]).
     *
     * Der manuelle Pull pausiert bewusst NICHT: Er ist derselbe Weg wie die Automatik, nur von Hand
     * ausgelöst, und darf sie nicht abwürgen.
     *
     * Pausiert wird nur dort, wo es eine Automatik gibt. Sonst sammelte jede Veranstaltung ohne
     * RaceClocker - und das sind nach der Migration erst einmal alle - an jedem von Hand
     * eingetragenen Lauf einen Vermerk ein, den die Oberfläche als „Automatischer Abruf pausiert"
     * anzeigt und der sich auf nichts bezieht. Die Prüfung steht im Repo des Jobs, damit die
     * Bedingung an genau einer Stelle formuliert ist.
     */
    private fun pauseRaceClockerAutoPull(matchId: UUID): App<Nothing, Unit> = KIO.comprehension {
        val configured = !RaceClockerPollRepo.isAutoPullConfigured(matchId).orDie()
        if (!configured) return@comprehension unit

        !CompetitionMatchRepo.update(matchId) {
            if (raceclockerAutoPausedAt == null) {
                raceclockerAutoPausedAt = LocalDateTime.now()
            }
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
        !pauseRaceClockerAutoPull(matchId)

        !prepareForNewPlaces(matchId)

        val noPlaces = request.teamResults.filter { !it.failed }.any { it.place == null }

        // Validate places are continuous when provided
        if (!noPlaces) {
            val places = request.teamResults.filter { !it.failed }.mapNotNull { it.place }.sorted()
            places.forEachIndexed { index, place ->
                val expected = index + 1
                !KIO.failOn(expected != place) {
                    CompetitionExecutionError.PlacesNotContinuous(expected = expected, actual = place)
                }
            }
        }

        val calculatedPlaces: List<Pair<UUID, Timecode?>> =
            request.teamResults.filter { !it.failed }
                .map { result ->
                    result.registrationId to result.timeString?.let { timestring -> (!Parser.timecode(timestring) { it.orDie() }) }
                }
                .sortedBy { it.second?.millis }

        !request.teamResults.traverse { result ->
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
        }

        // Ein Lauf kann beendet sein und erst mit dieser Eingabe vollständig gewertet werden -
        // dann ist das Ergebnis der letzte fehlende Baustein der Runde.
        !AutoRoundProgressionService.progressIfRoundComplete(eventId, competitionId, userId)

        // Handeingaben sind genau die Korrekturen, die sofort auf die Anzeigen sollen.
        EventChangeMarker.bump(eventId)

        noData
    }

    /**
     * Der Notfallweg zum Live-Abruf: eine von RaceClocker heruntergeladene Ergebnis-xlsx auf einen
     * Lauf schreiben, wenn am Steg das Netz fehlt. Liest das „Results"-Blatt
     * ([RaceClockerResultsXls]) und reicht die Zeilen durch dieselbe Schreiblogik wie der Live-Abruf
     * ([applyRaceClockerRows]) — Zuordnung über die R2R-Kennung aus „Extra info", Platz aus den
     * Zeiten, Duplikat- und Reset-Prüfung inklusive.
     *
     * Der automatische Abruf wird dabei pausiert (wie beim Tabellen-Upload): Wer von Hand eine Datei
     * einspielt, hat das letzte Wort, sonst überschriebe der nächste Takt es wieder. Freigabe über
     * „Automatik wieder aufnehmen".
     *
     * Bewusste Grenze: Die xlsx trägt weder Startzeiten noch Zwischenzeiten, deshalb löscht dieser
     * Import einen zuvor vom Live-Abruf geschriebenen Boot-Start und dessen Runden — die Datei ist im
     * Moment des Imports die Quelle der Wahrheit.
     */
    fun importRaceClockerResultsFile(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        file: File,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val match = !checkUpdateMatchResult(competitionId, matchId, byeError = RaceClockerError.MatchIsBye)
        !pauseRaceClockerAutoPull(matchId)

        val target = !CompetitionMatchRepo.getForRaceClockerPull(matchId).orDie()
            .onNullFail { CompetitionExecutionError.MatchNotFound }

        val rows = when (val result = RaceClockerResultsXls.parse(file.bytes)) {
            is RaceClockerResultsXls.ParseResult.Ok -> result.rows
            RaceClockerResultsXls.ParseResult.Invalid ->
                !KIO.fail(CompetitionExecutionError.ResultUploadError.FileError)
        }

        !applyRaceClockerRows(match, matchId, target, rows, userId)

        !AutoRoundProgressionService.progressIfRoundComplete(eventId, competitionId, userId)

        // Datei-Import ist ein Ergebnis-Schreiber wie die Handeingabe.
        EventChangeMarker.bump(eventId)

        noData
    }

    fun updateMatchResultByFile(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        file: File,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val match = !checkUpdateMatchResult(competitionId, matchId)
        !pauseRaceClockerAutoPull(matchId)
        !prepareForNewPlaces(matchId)

        // Das Format gehoert zum Wettkampf (Zeitnahme-Tab), nicht mehr zur einzelnen Anfrage --
        // und der Wettkampf erbt es von der Veranstaltung, solange er selbst keines gesetzt hat
        // (Migration V202608071300, dieselbe Regel wie beim Startlisten-Export).
        val competition = !CompetitionRepo.getRecordById(competitionId).orDie()
            .onNullFail { CompetitionError.CompetitionNotFound }
        val event = !EventRepo.get(eventId).orDie()
            .onNullFail { EventError.NotFound }
        val configId = !KIO.failOnNull(competition.resultImportConfig ?: event.resultImportConfig) {
            MatchResultImportConfigError.NotConfigured
        }
        val config = !MatchResultImportConfigRepo.get(configId).orDie()
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
                noResultReason = timeCell?.takeUnless { timeIsValid },
                // Der Tabellen-Upload trägt bewusst keine Zeitstrafen: Strafen kommen aus der
                // Zeitmessung (RaceClocker) oder werden im Ergebnis-Formular erfasst.
                penaltySeconds = null,
                penaltyNote = null,
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
        !applyParsedTeamResults(match, matchId, teams, userId)

        // Ein Lauf kann beendet sein und erst mit dieser Eingabe vollständig gewertet werden -
        // dann ist das Ergebnis der letzte fehlende Baustein der Runde.
        !AutoRoundProgressionService.progressIfRoundComplete(eventId, competitionId, userId)

        // Datei-Import ist ein Ergebnis-Schreiber wie die Handeingabe.
        EventChangeMarker.bump(eventId)

        noData
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
        // assigned.
        if (correctedTeams.isNotEmpty() && correctedTeams.all { it.startNumber != null }) {
            !writeStartNumbers(matchId, correctedTeams.associate { it.registrationId to it.startNumber!! }, userId)
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
                    // Nur ausweisen: die Zeit enthält die Strafe bereits, sie wird nicht verrechnet.
                    this.penaltySeconds = result.penaltySeconds
                    this.penaltyNote = result.penaltyNote
                    updatedBy = userId
                    updatedAt = LocalDateTime.now()
                }.orDie().onNullFail { CompetitionExecutionError.MatchTeamNotFound }
            }
        }.noDataResponse()


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
     * and can simply be repeated once RaceClocker has been corrected. Das Schreiben selbst steht in
     * [applyRaceClockerRows]; hier bleibt nur das Holen. Die Trennung ist der Punkt: Der automatische
     * Abruf (`RaceClockerPollService`) holt denselben Feed einmal je Rennen für alle Läufe gemeinsam
     * und ruft dann dieselbe Anwendungslogik auf. Läge sie hier im Endpunkt, gäbe es zwei Wege,
     * Ergebnisse zu schreiben, und sie würden auseinanderlaufen.
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

        // Genau ein Rennen je Wettkampf (11.08.2026) - gibt es keines, gibt es nichts zu holen.
        val rawUrl = target.resultsUrl ?: return KIO.fail(RaceClockerError.UrlMissing)

        val url = !RaceClockerFeed.normalizeUrl(rawUrl)
        val rows = !RaceClockerFeed.fetch(RaceClockerFeed.feedUrl(url))

        val response = !applyRaceClockerRows(match, matchId, target, rows, userId)

        // Der manuelle Pull schreibt denselben Stand wie die Automatik — und soll ihn genauso
        // sofort auf den Anzeigen zeigen.
        EventChangeMarker.bump(eventId)

        return KIO.ok(response)
    }

    /**
     * Gibt einen pausierten Lauf wieder für den automatischen Abruf frei. Löscht zugleich den
     * letzten Fehler - was beim nächsten Takt passiert, ist die Antwort, die jetzt zählt.
     */
    fun resumeRaceClockerAutoPull(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }
        !checkUpdateMatchResult(competitionId, matchId, byeError = RaceClockerError.MatchIsBye)

        !CompetitionMatchRepo.update(matchId) {
            raceclockerAutoPausedAt = null
            raceclockerPollError = null
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        // Der Job merkt sich je Lauf den zuletzt geschriebenen Stand und schreibt nichts, solange
        // der Feed unverändert ist. Nach einer Handeingabe beschreibt dieser Merkposten nicht mehr,
        // was in der Datenbank steht - ohne das Vergessen liefe der nächste Takt in die Abkürzung,
        // die Oberfläche meldete einen gesunden Abruf, und RaceClocker übernähme den Lauf erst
        // wieder, wenn sich dort irgendwann eine Zeile ändert. Wer "Automatik wieder aufnehmen"
        // drückt, will genau das Gegenteil: den nächsten Takt voll durchlaufen sehen.
        RaceClockerPollService.forget(matchId)

        // Bewusst kein EventChangeMarker.bump: Pause-Vermerk und Fehlerspalte zeigt kein
        // öffentliches Board, und der nächste Takt bumpt selbst, sobald er wirklich schreibt.
        noData
    }

    /**
     * Schreibt einen bereits geholten RaceClocker-Feed auf einen Lauf: Zuordnung, Duplikatprüfung,
     * `started_at`, Bahnen, Zeiten und Plätze.
     *
     * Ohne HTTP und ohne `CallComprehensionScope`, damit der Hintergrund-Job sie aufrufen kann. Was
     * hier fehlschlägt, ist derselbe Fehler wie beim Knopf — der Job schreibt ihn nur in eine Spalte,
     * statt ihn zu beantworten.
     */
    fun applyRaceClockerRows(
        match: CompetitionMatchWithTeams,
        matchId: UUID,
        target: RaceClockerMatchTarget,
        rows: List<RaceClockerFeedRow>,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {

        val teams = match.teams.filter { !it.deregistered }
        val rowsByTeam = assignFeedRows(rows, teams, target.waveName)

        if (rowsByTeam.isEmpty()) return@comprehension KIO.fail(RaceClockerError.MatchNotInFeed(target.candidateUrls, target.candidateRaceNames))

        // RaceClocker only ever inserts, it never updates: importing the same start list twice leaves
        // duplicate crews behind. Picking one of them silently would be a coin flip, so we refuse and
        // let the user clean up there.
        val duplicates = rowsByTeam.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            return@comprehension KIO.fail(
                RaceClockerError.DuplicateTeams(target.waveName, duplicates.values.map { it.first().name })
            )
        }

        // Lanes are taken from every assigned row, not just the timed ones: a heat is usually pulled
        // while boats are still on the water, and numbering only the finishers would push the rest
        // out of their lanes.
        //
        // Sie stehen deshalb vor BEIDEN Riegeln darunter - dem Reset-Zweig und dem Ergebnis-Riegel.
        // Eine Bahn hängt an der Startliste, nicht an einer Zeit: Sobald in RaceClocker umsortiert
        // wird, gilt die neue Reihenfolge, und genau davor - Lauf am Start, jede Zeile auf
        // `Not started` - will der Schiedsrichter sie auf dem Board sehen. Stand die Vergabe hinter
        // den Riegeln, kam ein Bahnentausch nie an, solange kein Boot durchs Ziel war (beobachtet am
        // 09.08.2026 an der CRF-Testregatta: der Abruf meldete still "keine Ergebnisse" und ließ die
        // Bahnen stehen). Für den Reset-Zweig gilt dasselbe: Eine zurückgesetzte Welle wird oft
        // zugleich neu sortiert, und C30 verlangt nur, dass der Reset die Bahnen nicht *löscht* -
        // aus dem Feed geschrieben sind sie bei unveränderter Reihenfolge dieselben.
        !applyLanesFromFeed(matchId, rowsByTeam.mapValues { (_, matchedRows) -> matchedRows.single() }, userId)

        // Zwischenzeiten stehen wie die Bahnen vor beiden Riegeln: Sie kommen, während die Boote
        // noch fahren, und eine in RaceClocker korrigierte Marke soll ankommen, ohne dass ein Boot
        // erst im Ziel sein muss. Je Takt werden die Runden eines Boots vollständig ersetzt.
        !applyLapsFromFeed(matchId, rowsByTeam.mapValues { (_, matchedRows) -> matchedRows.single() }, userId)

        // Neustart in RaceClocker: keine Zeile trägt mehr eine gemessene Startzeit oder ein Ergebnis.
        // Der Zeitnehmer setzt eine Welle zurück, wenn ein Start ungültig war; der Feed sagt danach,
        // dieser Lauf sei nie gefahren, und ready2race übernimmt diese Aussage - sonst stünden Zeiten
        // und Plätze des ungültigen Laufs weiter da, während RaceClocker längst neu misst.
        //
        // Die Bedingung ist bewusst dieselbe wie beim Aktivieren ([RaceClockerPollLogic.startDetected]),
        // nur andersherum gelesen: Was einen Lauf starten lässt, hält ihn auch gestartet. Ohne die
        // Startzeit im Zweig träfe der Reset den Normalfall "alle noch auf dem Wasser" - dort trägt
        // ebenfalls keine Zeile ein Ergebnis - und nähme einem laufenden Rennen die bereits
        // eingelaufenen Boote wieder weg.
        if (!RaceClockerPollLogic.startDetected(rowsByTeam.values.flatten())) {
            return@comprehension resetRaceClockerResults(matchId, rowsByTeam.keys, userId)
        }

        // Einzelne Rücknahme: Die Welle läuft weiter (Start erkannt), aber eine zugeordnete Zeile
        // hat kein verwertbares Ergebnis mehr, obwohl bei uns eines steht - der Zeitnehmer hat eine
        // versehentlich genommene Zeit in RaceClocker wieder entfernt (beobachtet am 10.08.2026).
        // Solange der Abruf läuft, ist RaceClocker die Quelle der Wahrheit - die Zeit geht mit.
        //
        // Ausgeschiedene Boote ([failed]) bleiben ausdrücklich stehen: Eine vom Schiedsrichter von
        // Hand eingetragene Ausscheidung hat im Feed nie ein Ergebnis, und genau sie schützt der
        // Riegel weiter unten seit jeher. Der Preis: Eine in RaceClocker zurückgenommene DNF-Wertung
        // muss auch bei uns von Hand zurückgenommen werden.
        val removedResults = rowsByTeam.filterValues { !it.single().hasResult }.keys
        !retractRemovedResults(matchId, removedResults, userId)

        // Crews without a usable result are skipped rather than treated as an error, so the pull can
        // be repeated as the heat progresses. "Ohne Ergebnis" heißt hier: weder Zeit noch echte
        // Ausscheidung - RaceClocker schreibt in dieselbe Spalte auch Verlaufszustände (`Not started`,
        // `In race...`), und ein solcher Text als Ausscheidungsgrund würde ein noch fahrendes Boot als
        // ausgeschieden markieren (siehe [RaceClockerFeedRow.noResultReason]). Ein bloßes
        // `result != null` reicht dafür nicht.
        //
        // Übersprungene Zeilen bleiben in der DB unangetastet: eine vom Schiedsrichter von Hand
        // eingetragene Ausscheidung darf ein Nachziehen des Laufs nicht wieder löschen.
        val withResult = rowsByTeam.mapNotNull { (registrationId, matchedRows) ->
            matchedRows.single().takeIf { it.hasResult }?.let { registrationId to it }
        }
        // Noch kein Ergebnis im Feed ist der Normalfall eines Laufs am Start und kein Fehler mehr:
        // Die Bahnen oben SIND das Ergebnis dieses Abrufs. Ein `KIO.fail` würde sie hier wieder
        // verwerfen - der Hintergrund-Job umschließt diese Funktion mit `transact()`, und der
        // Endpunkt hängt in der Transaktion von `respondComprehension`.
        if (withResult.isEmpty()) return@comprehension noData

        // Externe Zeitnahme ist Quelle der Wahrheit: die früheste von RaceClocker gemessene
        // Startzeit unter den zugeordneten Booten überschreibt started_at bedingungslos, auch wenn
        // dort schon ein manueller Stempel steht (z. B. von LiveDashboardService.markMatchStarted) -
        // gleiche Regel wie beim Penalty-Überschreiben oben.
        // Der Tag kommt NICHT vom geplanten Renntag: läuft ein Rennen an einem anderen Tag als
        // geplant, stünde der Stempel Tage daneben (siehe RaceClockerPollLogic.stampOnNearestDay -
        // dieselbe Regel wie beim vorgezogenen Stempel im Poll-Job).
        val earliestStart = RaceClockerFeedRow.earliestStart(rowsByTeam.values.flatten())
        if (earliestStart != null) {
            !CompetitionMatchRepo.update(matchId) {
                startedAt = RaceClockerPollLogic.stampOnNearestDay(earliestStart, LocalDateTime.now())
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie()
        }

        !prepareForNewPlaces(matchId)

        val parsed = withResult.map { (registrationId, row) ->
            ParsedTeamResult(
                registrationId = registrationId,
                // Lanes were written above from the feed's list positions; leaving this null keeps
                // [applyParsedTeamResults] from renumbering them off the bib, which stays with a boat
                // when it is moved and therefore no longer describes where it starts.
                startNumber = null,
                // Places are derived from the times further down - RaceClocker's "Rank" is a list
                // position, not a finishing rank.
                place = null,
                time = row.time,
                noResultReason = row.noResultReason,
                penaltySeconds = row.penaltySeconds,
                penaltyNote = row.penaltyNote,
            )
        }

        !applyParsedTeamResults(match, matchId, parsed, userId)

        // Ein Lauf kann beendet sein und erst mit dieser Eingabe vollständig gewertet werden -
        // dann ist das Ergebnis der letzte fehlende Baustein der Runde. Weder Event- noch
        // Wettkampf-Id stehen hier als Parameter zur Verfügung - die Aufrufer sind der
        // RaceClocker-Job (`RaceClockerPollService`) und der manuelle Pull
        // (`updateMatchResultFromRaceClocker`), beide kennen nur den Lauf - deshalb die Variante,
        // die Wettkampf und Veranstaltung selbst nachschlägt.
        !AutoRoundProgressionService.progressAfterMatch(matchId, userId)

        noData
    }

    /**
     * Nimmt einem Lauf alles zurück, was aus einem gefahrenen Rennen stammt: Zeit, Platz,
     * Ausscheidung, Strafzeit und den Ist-Start.
     *
     * Angefasst werden nur die Mannschaften, deren Zeilen im Feed stehen ([registrationIds]) - ein
     * Boot, das RaceClocker gar nicht kennt, hat sein Ergebnis von Hand bekommen und geht diesen Weg
     * nichts an.
     *
     * Die Bahnen bleiben stehen: Die Zeilen stehen weiterhin im Feed, nur ohne Zeiten, und bis der
     * Zeitnehmer sie umsortiert, ist die Aufstellung des neuen Versuchs dieselbe. `activated_at`
     * bleibt ebenfalls unangetastet - ein Schiedsrichter hat den Lauf an den Start gerufen, und dass
     * die Zeitnahme neu aufsetzt, nimmt ihm diese Entscheidung nicht ab.
     */
    /**
     * Nimmt Zeiten und Plätze einzelner Boote zurück, deren Feed-Zeile kein verwertbares Ergebnis
     * mehr trägt - das Gegenstück zu [resetRaceClockerResults] für den Fall, dass nur EINE Zeit in
     * RaceClocker entfernt wurde, während die Welle weiterläuft.
     *
     * Ausgeschiedene Boote ([CompetitionMatchTeamRecord.failed]) bleiben unangetastet - eine von
     * Hand eingetragene Ausscheidung hat im Feed nie ein Ergebnis und wäre sonst bei jedem Abruf
     * wieder weg. Boote ohne gespeicherte Zeit und ohne Platz sind der Normalfall "noch auf dem
     * Wasser" und werden gar nicht erst angefasst.
     */
    private fun retractRemovedResults(
        matchId: UUID,
        registrationIds: Set<UUID>,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val now = LocalDateTime.now()

        !registrationIds.toList().traverse { registrationId ->
            KIO.comprehension {
                val record = !CompetitionMatchTeamRepo.getByMatchAndRegistrationId(matchId, registrationId).orDie()
                    .onNullFail { CompetitionExecutionError.MatchTeamNotFound }

                val hasStoredResult =
                    record.timecode != null || record.place != null || record.penaltySeconds != null
                if (record.failed == true || !hasStoredResult) return@comprehension noData

                !TimecodeRepo.delete(record.id).orDie()

                logger.info {
                    "RaceClocker hat das Ergebnis von Boot $registrationId in Lauf $matchId zurückgenommen."
                }

                !CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, registrationId) {
                    place = null
                    placesCalculated = false
                    timecode = null
                    penaltySeconds = null
                    penaltyNote = null
                    updatedBy = userId
                    updatedAt = now
                }.orDie()

                noData
            }
        }

        noData
    }

    private fun resetRaceClockerResults(
        matchId: UUID,
        registrationIds: Set<UUID>,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val now = LocalDateTime.now()

        !registrationIds.toList().traverse { registrationId ->
            KIO.comprehension {
                val record = !CompetitionMatchTeamRepo.getByMatchAndRegistrationId(matchId, registrationId).orDie()
                    .onNullFail { CompetitionExecutionError.MatchTeamNotFound }

                // Der Fremdschlüssel steht auf `on delete set null`; die Spalte wird unten trotzdem
                // ausdrücklich geleert, damit hier nicht zwei Stellen dasselbe zusagen müssen.
                !TimecodeRepo.delete(record.id).orDie()

                // Ein Lauf, der laut Feed nie gefahren ist, hat auch keine Zwischenzeiten mehr.
                !CompetitionMatchTeamLapRepo.deleteByTeam(record.id!!).orDie()

                CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, registrationId) {
                    place = null
                    placesCalculated = false
                    failed = false
                    failedReason = null
                    timecode = null
                    penaltySeconds = null
                    penaltyNote = null
                    startedAt = null
                    updatedBy = userId
                    updatedAt = now
                }.orDie()
            }
        }

        !CompetitionMatchRepo.update(matchId) {
            startedAt = null
            updatedBy = userId
            updatedAt = now
        }.orDie()

        logger.info { "RaceClocker hat den Lauf $matchId zurückgesetzt - Zeiten, Plätze und Ist-Start gelöscht." }

        noData
    }

    /**
     * Writes the lanes a match currently has in RaceClocker onto its teams.
     *
     * The timekeeper swaps lanes on site, and everything attached to an entry travels with it when it
     * is moved - only the list position changes. [RaceClockerFeedRow.lanesByRow] turns those positions
     * into 1..n, and this writes them to the start numbers.
     *
     * Start numbers are negated first to avoid transient violations of the unique
     * (competition_match, start_number) index while reassigning; teams that the feed does not cover
     * keep a unique number after the highest imported one.
     */
    /**
     * Schreibt den gemessenen Start des einzelnen Boots und seine Zwischenzeiten (Split-Spalten)
     * aus dem Feed.
     *
     * Der Boot-Start trägt die "wer ist schon unterwegs?"-Anzeige beim Zeitfahren (Einzelstarts):
     * dort startet jedes Boot einzeln, und `competition_match.started_at` sagt nur, dass IRGENDWER
     * gestartet ist. Zwischenzeiten werden je Marke als kumulierte Fahrzeit seit dem gemessenen
     * Start der ZEILE gespeichert - bei Wellenstarts ist das der Wellenstart, bei Einzelstarts der
     * eigene. Läuft eine Marke über Mitternacht, korrigiert ein Tagessprung die Differenz -
     * dieselbe Datumsregel wie `RaceClockerPollLogic.stampOnNearestDay`.
     *
     * Beides wird je Takt vollständig aus dem Feed ersetzt: eine in RaceClocker zurückgenommene
     * Startzeit oder Marke verschwindet auch hier wieder.
     */
    private fun applyLapsFromFeed(
        matchId: UUID,
        rowByRegistration: Map<UUID, RaceClockerFeedRow>,
        userId: UUID,
    ): App<Nothing, Unit> = KIO.comprehension {
        val allMatchTeamRecords = !CompetitionMatchTeamRepo.getByMatch(matchId).orDie()
        val now = LocalDateTime.now()

        !allMatchTeamRecords.traverse { team ->
            KIO.comprehension {
                val row = rowByRegistration[team.competitionRegistration]
                    ?: return@comprehension KIO.unit
                val start = row.start

                val newStartedAt = start?.let { RaceClockerPollLogic.stampOnNearestDay(it, now) }
                if (team.startedAt != newStartedAt) {
                    !CompetitionMatchTeamRepo.update(team) {
                        startedAt = newStartedAt
                        updatedBy = userId
                        updatedAt = now
                    }.orDie()
                }

                !CompetitionMatchTeamLapRepo.deleteByTeam(team.id!!).orDie()

                if (start != null) {
                    val records = row.laps.mapIndexed { index, lap ->
                        var millis = java.time.Duration.between(start, lap.time).toMillis()
                        if (millis < 0) millis += java.time.Duration.ofDays(1).toMillis()
                        CompetitionMatchTeamLapRecord(
                            id = UUID.randomUUID(),
                            competitionMatchTeam = team.id!!,
                            position = index + 1,
                            name = lap.name,
                            lapMillis = millis,
                            createdAt = now,
                            createdBy = userId,
                        )
                    }
                    if (records.isNotEmpty()) {
                        !CompetitionMatchTeamLapRepo.create(records).orDie()
                    }
                }

                KIO.unit
            }
        }

        KIO.unit
    }

    private fun applyLanesFromFeed(
        matchId: UUID,
        rowByRegistration: Map<UUID, RaceClockerFeedRow>,
        userId: UUID,
    ): App<Nothing, Unit> = KIO.comprehension {
        val lanes = RaceClockerFeedRow.lanesByRow(rowByRegistration)
        if (lanes.isEmpty()) return@comprehension KIO.unit

        writeStartNumbers(matchId, lanes, userId)
    }

    /**
     * Sets the start numbers of a match from [startNumberByRegistration].
     *
     * They are negated first to avoid transient violations of the unique (competition_match,
     * start_number) index while reassigning. Teams the caller does not cover (e.g. deregistered or
     * out teams) keep a unique number after the highest given one.
     */
    private fun writeStartNumbers(
        matchId: UUID,
        startNumberByRegistration: Map<UUID, Int>,
        userId: UUID,
    ): App<Nothing, Unit> = KIO.comprehension {
        val allMatchTeamRecords = !CompetitionMatchTeamRepo.getByMatch(matchId).orDie()

        !allMatchTeamRecords.traverse { team ->
            CompetitionMatchTeamRepo.update(team) {
                startNumber = team.startNumber * -1
            }.orDie()
        }

        var fallbackStartNumber = startNumberByRegistration.values.maxOrNull() ?: 0
        !allMatchTeamRecords.sortedBy { it.startNumber }.traverse { team ->
            val newStartNumber = startNumberByRegistration[team.competitionRegistration]
                ?: (++fallbackStartNumber)
            CompetitionMatchTeamRepo.update(team) {
                startNumber = newStartNumber
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie()
        }

        KIO.unit
    }

    /**
     * Delegiert an [RaceClockerAssignmentLogic.assignFeedRows] - reine, ohne Datenbank testbare Logik,
     * extrahiert nach dem Muster von AthleteBoardLogic/EventScheduleLogic.
     */
    private fun assignFeedRows(
        rows: List<RaceClockerFeedRow>,
        teams: List<CompetitionMatchTeamWithRegistration>,
        waveName: String?,
    ): Map<UUID, List<RaceClockerFeedRow>> = RaceClockerAssignmentLogic.assignFeedRows(rows, teams, waveName)

    /**
     * Welche Feed-Zeilen zu diesem Lauf gehören - flach, ohne Zuordnung zur Mannschaft. Der
     * automatische Abruf braucht das vor dem Schreiben: um zu erkennen, ob die Welle überhaupt im
     * Feed steht, ob sie gestartet ist und ob sich seit dem letzten Takt etwas geändert hat.
     */
    fun assignedRowsFor(
        rows: List<RaceClockerFeedRow>,
        teams: List<CompetitionMatchTeamWithRegistration>,
        waveName: String?,
    ): List<RaceClockerFeedRow> = assignFeedRows(rows, teams, waveName).values.flatten()

    /**
     * Der „Läuft"-Klick des Regattabüros: hält den Ist-Start fest, identisch zu
     * `LiveDashboardService.markMatchStarted` (idempotent, aktiviert nebenbei). Er liegt zusätzlich
     * auf der Durchführungs-Route, weil der Ist-Start keine Schiedsrichter-Exklusivität ist —
     * das Büro stellt denselben Sachverhalt fest, nur von seinem Arbeitsplatz aus.
     */
    fun markMatchStarted(
        eventId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }
        !CompetitionMatchRepo.exists(matchId).orDie().onNullFail { CompetitionExecutionError.MatchNotFound }

        !CompetitionMatchRepo.update(matchId) {
            if (startedAt == null) {
                startedAt = LocalDateTime.now()
            }
            if (activatedAt == null) {
                activatedAt = LocalDateTime.now()
            }
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        // „Läuft" soll sofort auf den Anzeigen stehen, nicht erst nach Ablauf der Cache-TTL.
        EventChangeMarker.bump(eventId)

        noData
    }

    /**
     * Nimmt das Beenden eines Laufs zurück (`finished_at` weg) — der Weg zurück aus einem
     * versehentlichen Beenden-Klick, auch für ein versehentlich quittiertes Freilos.
     *
     * Erlaubt nur in der jüngsten Runde: dieselbe Sperre wie bei Ergebniskorrekturen. Ist aus dem
     * Beenden bereits eine Folgerunde entstanden, muss die wie bei jeder Korrektur zuerst gelöscht
     * werden — die Rücknahme rollt bewusst nichts davon zurück, sie nimmt nur den Stempel.
     * Aktivierung und Ist-Start bleiben ebenfalls stehen: Was tatsächlich passiert ist, bleibt
     * festgehalten; der Lauf kehrt in den Zustand „läuft/wartet auf Beenden" zurück.
     */
    fun reopenMatch(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        !KIO.failOn(setupRounds.flatMap { it.setupMatches.toList() }.none { it.id == matchId }) {
            CompetitionExecutionError.MatchNotFound
        }

        // Nicht checkUpdateMatchResult: Das würde Freilose abweisen, und gerade deren Quittierung
        // soll rücknehmbar sein. Die Rundensperre selbst ist dieselbe.
        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)
        val match = currentRound.matches.find { it.competitionSetupMatch == matchId }
            ?: return@comprehension KIO.fail(CompetitionExecutionError.MatchResultsLocked)

        !KIO.failOn(match.finishedAt == null) { CompetitionExecutionError.MatchNotFinished }

        !CompetitionMatchRepo.update(matchId) {
            finishedAt = null
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        logger.info { "Beenden von Lauf $matchId zurückgenommen." }

        // Der Lauf wandert auf den Anzeigen aus den Ergebnissen zurück.
        EventChangeMarker.bump(eventId)

        noData
    }

    /**
     * Setzt einen einzelnen Lauf in den Zustand „nie gefahren" zurück, ohne seine Zeilen zu
     * löschen - die Alternative zum Löschen der ganzen Runde, wenn nur EIN Lauf neu gefahren
     * werden muss.
     *
     * Der Punkt ist der Erhalt der UUIDs: `deleteCurrentRound` legt beim Neuerstellen neue
     * `competition_match_team`-Zeilen an, und deren Kennungen stecken in RaceClocker-Extra-infos,
     * exportierten Startlisten und allen Verweisen darauf. Dieser Weg behält die Zeilen und leert
     * nur, was aus dem gefahrenen Rennen stammt.
     *
     * Geleert wird der Ausführungszustand:
     * - `competition_match`: `activated_at`, `started_at`, `finished_at`, dazu die Abruf-Vermerke
     *   `raceclocker_auto_paused_at` und `raceclocker_poll_error` - ein zurückgesetzter Lauf soll
     *   ohne weiteren Handgriff wieder automatisch abgerufen werden. `raceclocker_polled_at`
     *   bleibt: es beschreibt den Job, nicht den Lauf.
     * - `competition_match_team`: Platz, Ausscheidung ([CompetitionMatchTeamRecord.failed] samt
     *   Grund), Zeit (Timecode-Zeile wird gelöscht), Strafzeit, Boot-Start und alle Rundenzeiten -
     *   genau die Felder, die [updateMatchResult] und [applyParsedTeamResults] schreiben.
     *
     * Stehen bleibt die Struktur: Aufstellung, Bahnen (`start_number`), geplante Startzeit und
     * `pairings_recalculated_at`. Auch [CompetitionMatchTeamRecord.out] bleibt - es markiert das
     * Ausscheiden aus einer FRÜHEREN Runde (gesetzt bei der Rundenerzeugung aus
     * `deregistered || out || failed` der Vorrunde), ist also kein Ergebnis dieses Laufs.
     *
     * Erlaubt nur, solange die Folgerunde noch keine erzeugten Läufe hat - dieselbe Stromrichtung
     * wie [deleteCurrentRound]. Danach hat das Ergebnis die nächste Runde gesät, und der Weg führt
     * wie bei jeder Korrektur über das Löschen der Folgerunde ([ResetBlockedByNextRound]).
     *
     * Bewusst ohne [checkUpdateMatchResult]: Das würde Freilose abweisen (wie bei [reopenMatch]),
     * und auch ein versehentlich quittiertes Freilos soll sich zurücksetzen lassen.
     */
    fun resetMatch(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        !KIO.failOn(setupRounds.flatMap { it.setupMatches.toList() }.none { it.id == matchId }) {
            CompetitionExecutionError.MatchNotFound
        }

        val currentRound = getCurrentAndNextRound(setupRounds).first
            ?: return@comprehension KIO.fail(CompetitionExecutionError.NoRoundsInSetup)
        currentRound.matches.find { it.competitionSetupMatch == matchId }
            ?: return@comprehension KIO.fail(CompetitionExecutionError.ResetBlockedByNextRound)

        val now = LocalDateTime.now()
        val teams = !CompetitionMatchTeamRepo.getByMatch(matchId).orDie()

        !teams.traverse { team ->
            KIO.comprehension {
                // Der Fremdschlüssel steht auf `on delete set null`; die Spalte wird unten trotzdem
                // ausdrücklich geleert - dasselbe Muster wie in [resetRaceClockerResults].
                !TimecodeRepo.delete(team.id).orDie()
                !CompetitionMatchTeamLapRepo.deleteByTeam(team.id).orDie()

                !CompetitionMatchTeamRepo.update(team) {
                    place = null
                    placesCalculated = false
                    failed = false
                    failedReason = null
                    timecode = null
                    penaltySeconds = null
                    penaltyNote = null
                    startedAt = null
                    updatedBy = userId
                    updatedAt = now
                }.orDie()

                unit
            }
        }

        !CompetitionMatchRepo.update(matchId) {
            activatedAt = null
            startedAt = null
            finishedAt = null
            raceclockerAutoPausedAt = null
            raceclockerPollError = null
            updatedBy = userId
            updatedAt = now
        }.orDie()

        // Der Job merkt sich je Lauf den zuletzt geschriebenen Stand und schreibt nichts, solange
        // der Feed unverändert ist. Nach dem Reset beschreibt dieser Merkposten nicht mehr, was in
        // der Datenbank steht - ohne das Vergessen bliebe der Lauf leer, bis sich in RaceClocker
        // irgendwann eine Zeile ändert (gleiche Falle wie bei [resumeRaceClockerAutoPull]).
        RaceClockerPollService.forget(matchId)

        logger.info { "Lauf $matchId zurückgesetzt - Ausführungszustand geleert, Aufstellung und Kennungen bleiben." }

        // Zeiten und Zustand des Laufs verschwinden von den Anzeigen.
        EventChangeMarker.bump(eventId)

        noData
    }

    fun updateMatchActivation(
        matchId: UUID,
        userId: UUID,
        request: UpdateCompetitionMatchActivationRequest,
        eventId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        !CompetitionMatchRepo.exists(matchId).orDie().onNullFail { CompetitionExecutionError.MatchNotFound }

        !setMatchActivation(matchId, request.activated, userId)

        // setMatchActivation selbst kennt die Veranstaltung nicht — beide Aufrufer (hier und
        // LiveDashboardService.setMatchActivated) bumpen deshalb selbst.
        EventChangeMarker.bump(eventId)

        noData
    }

    /**
     * Ruft einen Lauf an den Start oder nimmt das zurück — die eine Stelle für beide Wege dorthin
     * (Durchführungs-Tab über [updateMatchActivation], Schiedsrichter-Dashboard über
     * `LiveDashboardService.setMatchActivated`, dazu die Legacy-Aktivierung nach dem Beenden).
     *
     * Aktivieren setzt ausschließlich `activated_at`: Der Klick stellt fest, dass der Lauf
     * drankommt, nicht dass er fährt. Der Ist-Start kommt aus der Zeitnahme oder aus
     * `LiveDashboardService.markMatchStarted`. Ein erneutes Aktivieren rückt den Zeitpunkt nicht
     * vor — "seit wann steht der Lauf am Start" soll nicht bei jedem Klick neu beginnen.
     *
     * Deaktivieren nimmt beides zurück und pausiert zugleich den automatischen RaceClocker-Abruf.
     * Ohne die Pause wäre die Rücknahme wirkungslos: Der Lauf steht im Beobachtungsfenster, der Job
     * findet die Startzeit im Feed und aktiviert ihn im nächsten Takt wieder — spätestens nach 60
     * Sekunden. Freigegeben wird er über denselben Weg wie ein von Hand eingetragener Lauf
     * ([resumeRaceClockerAutoPull]).
     *
     * Die Funktion steht hier und nicht im Live-Dashboard, weil dieser Service ohnehin die
     * Schreibpfade auf `competition_match` hält (Ergebnisse, Pause, Freigabe) und
     * [pauseRaceClockerAutoPull] samt seiner Bedingung schon hier liegt; das Dashboard importiert
     * ihn längst. Die Alternative — die Logik im Dashboard und von hier aus aufrufen — hätte die
     * Abhängigkeit nur umgedreht und die Pause von ihrer Bedingung getrennt.
     *
     * Beenden geht bewusst NICHT über diesen Weg: Dort fällt zwar auch die Aktivierung, aber der
     * Ist-Start bleibt stehen (er ist eine Tatsache) und pausiert werden darf nichts.
     */
    fun setMatchActivation(matchId: UUID, activated: Boolean, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val now = LocalDateTime.now()
            !CompetitionMatchRepo.update(matchId) {
                if (activated) {
                    if (activatedAt == null) {
                        activatedAt = now
                    }
                } else {
                    activatedAt = null
                    startedAt = null
                }
                updatedBy = userId
                updatedAt = now
            }.orDie()

            if (!activated) {
                !pauseRaceClockerAutoPull(matchId)

                // Ohne das Vergessen des Merkpostens überspränge der nächste Takt die Änderung: Der
                // Job hält je Lauf den zuletzt geschriebenen Stand, und der beschreibt nach der
                // Rücknahme nicht mehr, was in der Datenbank steht.
                RaceClockerPollService.forget(matchId)
            }

            unit
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
            // Die gelöschten Läufe verschwinden aus „nächste Läufe" der Anzeigen.
            EventChangeMarker.bump(eventId)
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

    internal fun getSeedingList(
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

    /**
     * Liefert die Seeding-Liste, mit der [computeCompetitionPlaces] aus dem Platz im Lauf das
     * Rundenergebnis ("roundOutcome") macht — oder `null`, wenn die Platzvergabe der Runde ohne
     * Seeding auskommt.
     *
     * Nur ASCENDING und CUSTOM lesen die Liste; EQUAL vergibt allen einen festen Platz. Die
     * Bedingung an der Aufrufstelle war zuvor mit `!=` und `||` formuliert und damit immer wahr —
     * die Liste wurde also auch für EQUAL berechnet, dort aber nie ausgewertet. Das Ergebnis
     * ändert sich durch die Korrektur nicht, es wird nur nicht mehr unnötig gerechnet.
     *
     * ### Wie weit muss die Liste reichen?
     *
     * Beim Aufbau einer Folgerunde beantwortet [getSeedingList] die Frage "wer kommt weiter": dort
     * reicht [seedsForNextRound], die Zahl der Plätze in der Folgerunde. Bei der Platzvergabe ist
     * die Frage eine andere — hier geht es um alle Boote der Runde, gerade um die, die **nicht**
     * weiterkommen. Die Liste muss deshalb jeden Starter erreichen, also mindestens
     * [teamsInThisRound] Einträge hergeben.
     *
     * Das deckt sich mit dem Setup: die Platztabelle für CUSTOM wird im Frontend über die
     * Rundenergebnisse `1..thisRoundTeams` aufgespannt (`getNewPlaces` in `common.ts`), abzüglich
     * derer, die in die Folgerunde einziehen. Ohne Folgerunde ist dort der Platz schlicht das
     * Rundenergebnis — also die Reihenfolge im Lauf.
     *
     * Ein größeres Maximum verschiebt keine bestehende Zuordnung: die Verteilung läuft in fester
     * Schlangenreihenfolge und bricht nur früher ab. Alle Rundenergebnisse bis
     * [seedsForNextRound] landen daher an genau derselben Stelle wie bisher, es kommen nur die
     * Plätze dahinter neu dazu — "hinter allen Aufsteigern, in der Reihenfolge des Laufs".
     *
     * ### Was das repariert
     *
     * Beide bisherigen IndexOutOfBoundsExceptions beim Zugriff
     * `seedingList[matchIndex][realPlace - 1]` betrafen Massenfelder (`teams IS NULL`), weil nur
     * für sie das Maximum überhaupt begrenzend wirkt (siehe [getHighestTeamCount] und die
     * `null`-Bedingung in [getSeedingList]):
     * - **Letzte Runde**: keine Folgerunde, [seedsForNextRound] `= 0` — die Liste blieb leer
     *   (`[[]]`), obwohl im Massenfeld-Finale alle Boote einen Platz brauchen.
     * - **Qualifikation mit Nicht-Aufsteigern**: Zeitfahren als Massenfeld mit sechs Booten und
     *   vier Halbfinalplätzen ergab `[[1, 2, 3, 4]]` — die Boote auf Platz 5 und 6 fielen heraus.
     *
     * Bei fest gesetzten Bootszahlen ändert sich nichts, dort war die Liste schon immer so lang
     * wie der Lauf Boote hat. Ein Finale A/B behält damit seine Schlangenverteilung.
     */
    internal fun getPlacesSeedingList(
        placesOption: String,
        currentRoundTeams: List<Int?>,
        seedsForNextRound: Int,
        teamsInThisRound: Int,
    ): List<List<Int>>? =
        if (placesOption == CompetitionSetupPlacesOption.ASCENDING.name ||
            placesOption == CompetitionSetupPlacesOption.CUSTOM.name
        ) {
            getSeedingList(
                currentRoundTeams = currentRoundTeams,
                maxTeamsNeeded = maxOf(seedsForNextRound, teamsInThisRound),
            )
        } else null

    /**
     * Das Rundenergebnis ("roundOutcome") eines Bootes, das im Lauf [matchIndex] auf [realPlace]
     * gekommen ist.
     *
     * Die Seeding-Liste verteilt die Rundenergebnisse rechnerisch gleichmäßig auf die Läufe. Ist
     * ein Lauf tatsächlich voller als die Rechnung hergibt — mehrere Massenfelder in einer Runde
     * teilen sich das Feld, ohne dass im Setup steht, wie —, reicht sie nicht bis zu diesem Platz.
     * Dann gilt dieselbe Regel wie für die Nicht-Aufsteiger: hinter allen verteilten Ergebnissen,
     * in der Reihenfolge des Laufs. So bleibt die Platzvergabe auch dann auskunftsfähig, statt den
     * Abruf der ganzen Veranstaltung (Platzierungen, Urkunden) scheitern zu lassen.
     */
    internal fun getRoundOutcome(
        seedingList: List<List<Int>>,
        matchIndex: Int,
        realPlace: Int,
    ): Int {
        val seedingsOfMatch = seedingList.getOrNull(matchIndex) ?: emptyList()

        return seedingsOfMatch.getOrNull(realPlace - 1)
            ?: ((seedingList.flatten().maxOrNull() ?: 0) + realPlace - seedingsOfMatch.size)
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

        KIO.ok(computeCompetitionPlaces(sortRounds(setupRoundRecords)))
    }

    /**
     * Dieselbe Platzvergabe, aber an bereits geladenen Runden — [setupRounds] muss durch
     * [sortRounds] gegangen sein, die Rechnung liest die Nachbarrunde über den Index.
     *
     * Es gibt sie, weil [getSetupRoundsWithMatches][CompetitionSetupService.getSetupRoundsWithMatches]
     * die teuerste Abfrage des Wettkampfbereichs ist (Runden → Läufe → Teams → Teilnehmer). Wer die
     * Runden ohnehin braucht — der Siegerehrungsbogen sucht darin den Lauf je Boot —, fuhr sie
     * sonst ein zweites Mal ein, je Wettkampf.
     */
    fun computeCompetitionPlaces(
        setupRounds: List<CompetitionSetupRoundWithMatches>,
    ): List<Pair<CompetitionMatchTeamWithRegistration, Int>> {

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

                val seedingList = getPlacesSeedingList(
                    placesOption = round.placesOption,
                    currentRoundTeams = round.setupMatches.sortedBy { it.weighting }.map { it.teams },
                    // 0 = keine Folgerunde, also die letzte Runde
                    seedsForNextRound = setupRounds.getOrNull(roundIdx + 1)?.setupMatches?.sumOf { it.teams ?: 0 }
                        ?: 0,
                    // Alle Startenden der Runde, auch abgemeldete/ausgeschiedene — realPlace zählt sie mit
                    teamsInThisRound = round.matches.sumOf { it.teams.size },
                )


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
                            team to getRoundOutcome(seedingList!!, matchIndex, realPlace)

                        else -> {
                            val roundOutcome = getRoundOutcome(seedingList!!, matchIndex, realPlace)
                            // Für ein Massenfeld lässt sich im Setup keine Platztabelle pflegen, weil die
                            // Zahl der Startenden dort erst zur Laufzeit feststeht. Fehlt der Eintrag,
                            // gilt das Rundenergebnis selbst als Platz — wie bei ASCENDING.
                            team to (round.places.firstOrNull { it.roundOutcome == roundOutcome }?.place ?: roundOutcome)
                        }
                    }
                    teamToPlace
                }

                teamsToPlaces
            }


        return roundsWithTeamsToPlaces.flatten()
    }

    /**
     * Ein platziertes Boot mit beiden Plätzen nebeneinander: [place] wettkampfweit aus der
     * Rundenlogik, [categoryPlace] innerhalb seiner Wertungskategorie.
     */
    data class PlaceInCategory(
        val team: CompetitionMatchTeamWithRegistration,
        val place: Int,
        val categoryPlace: Int?,
    )

    /**
     * Gliedert die Platzierungen eines Wettkampfs in Abschnitte je Wertungskategorie und zählt in
     * jedem Abschnitt neu ab 1. [computeCompetitionPlaces] selbst bleibt unberührt und liefert
     * weiter die wettkampfweite Platzierung — daran hängen die Urkunden.
     *
     * Abgemeldete, ausgeschiedene und disqualifizierte Boote gelten hier als ungewertet: sie haben
     * zwar einen rechnerischen Platz, aber keinen, der in einer Ergebnisliste etwas zu suchen hat.
     * Sie behalten ihren Abschnitt und stehen dort am Ende.
     */
    fun placesByRatingCategory(
        places: List<Pair<CompetitionMatchTeamWithRegistration, Int>>,
    ): List<RankedCategory<PlaceInCategory>> =
        RatingCategoryRanking.groupAndRank(
            items = places.map { (team, place) -> PlaceInCategory(team, place, null) },
            category = { it.team.ratingCategory },
            place = { if (it.team.deregistered || it.team.out || it.team.failed) null else it.place },
            tieBreak = { it.team.startNumber },
        ).map { section ->
            section.copy(
                entries = section.entries.map { it.copy(item = it.item.copy(categoryPlace = it.categoryPlace)) }
            )
        }

    fun getCompetitionPlaces(
        eventId: UUID,
        competitionId: UUID,
        scope: Privilege.Scope?,
    ): App<ServiceError, ApiResponse.ListDto<CompetitionTeamPlaceDto>> = KIO.comprehension {

        !EventRepo.getScoped(eventId, scope).orDie().onNullFail { EventError.NotFound }

        computeCompetitionPlaces(competitionId)
            .andThen { places ->
                // In Abschnittsreihenfolge, innerhalb des Abschnitts nach Kategorieplatz - die
                // Anzeige gruppiert die flache Liste danach wieder auf.
                placesByRatingCategory(places)
                    .flatMap { it.entries.map { entry -> entry.item } }
                    .traverse { it.team.toCompetitionTeamPlaceDto(it.place, it.categoryPlace) }
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

            StartListFileType.CSV -> {
                val target = !CompetitionMatchRepo.getStartListConfigTarget(matchId).orDie()
                    .onNullFail { CompetitionExecutionError.MatchNotFound }
                val configId = !KIO.failOnNull(target.configId) { StartListConfigError.NotConfigured }
                val config = !StartListConfigRepo.get(configId).orDie()
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

    /**
     * Hängt die Startlisten-Zeilen EINES Laufs an eine Sammel-CSV an - der gemeinsame Baustein von
     * [downloadRoundStartlist] (eine Runde) und [buildEventStartlists] (die ganze Veranstaltung).
     *
     * Ein Lauf ohne Mannschaften wird übersprungen (Ergebnis null) statt den Export zu reißen; ein
     * Lauf ohne geplante Startzeit reißt ihn dagegen wie beim Einzel-Export - ohne Zeit gibt es
     * keinen brauchbaren Wellennamen. Die Kopfzeile schreibt nur der erste Lauf einer Datei
     * ([writeHeader]), und auch nur, wenn das Spalten-Preset sie nicht abbestellt hat.
     */
    private fun appendMatchCsv(
        setupMatchId: UUID,
        out: ByteArrayOutputStream,
        writeHeader: Boolean,
    ): App<ServiceError, String?> = KIO.comprehension {
        val record = !CompetitionMatchRepo.getForStartList(setupMatchId).orDie()
            .onNullFail { CompetitionExecutionError.MatchNotFound }
        if (record.teams!!.isEmpty()) return@comprehension KIO.ok(null)
        !KIO.failOn(record.startTime == null) { CompetitionExecutionError.StartTimeNotSet }

        val data = !CompetitionMatchData.fromPersisted(record)

        val target = !CompetitionMatchRepo.getStartListConfigTarget(setupMatchId).orDie()
            .onNullFail { CompetitionExecutionError.MatchNotFound }
        val configId = !KIO.failOnNull(target.configId) { StartListConfigError.NotConfigured }
        val config = !StartListConfigRepo.get(configId).orDie()
            .onNullFail { StartListConfigError.NotFound }

        out.write(buildCsv(data, config, includeHeader = writeHeader && config.noHeader != true))
        KIO.ok(data.competition.identifier)
    }

    /**
     * Die Startliste einer GANZEN Runde als eine CSV - ein Schwung für den Import ins
     * Zeitnahme-System statt Lauf für Lauf. Die Wellen unterscheidet RaceClocker über die
     * Wellenname-Spalte, die jede Zeile ohnehin trägt; die Kopfzeile schreibt nur die erste
     * Partie. Bewusst nur CSV: Die PDF-Startliste ist ein Aushang je Lauf, kein Importformat.
     */
    fun downloadRoundStartlist(
        eventId: UUID,
        competitionId: UUID,
        setupRoundId: UUID,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val round = setupRounds.find { it.setupRoundId == setupRoundId }
            ?: return@comprehension KIO.fail(CompetitionExecutionError.RoundNotFound)

        val matches = round.matches.sortedBy { it.startTime }
        !KIO.failOn(matches.isEmpty()) { CompetitionExecutionError.MatchNotFound }

        // Freilose fahren nicht und tauchen im RaceClocker nie auf - sie gehören nicht in den
        // Sammelexport der Runde (Wunsch vom 11.08.2026). Ein einzelnes Freilos-Boot als Startliste
        // zu exportieren, hieße dem Zeitnahme-System einen Lauf anzukündigen, den es nie sieht.
        // Ausnahme "muss gefahren werden" (bye_must_race): Dieses Freilos wird gefahren und braucht
        // seine Welle im Zeitnahme-System wie jeder Lauf.
        val byeByMatch = !MatchByeService.byeByMatch(eventId, competitionId)

        val out = ByteArrayOutputStream()
        var first = true
        var identifier = ""

        !matches.traverse { m ->
            KIO.comprehension {
                val bye = byeByMatch[m.competitionSetupMatch]
                if (bye != null && !bye.mustRace) return@comprehension unit

                val appended = !appendMatchCsv(m.competitionSetupMatch, out, writeHeader = first)
                if (appended != null) {
                    identifier = appended
                    first = false
                }

                unit
            }
        }

        KIO.ok(
            ApiResponse.File(
                name = "startList-$identifier-${round.setupRoundName}.csv",
                bytes = out.toByteArray(),
            )
        )
    }

    /**
     * Der Plan des Startlisten-Sammelexports (Zeitplan-Tab): je Wettkampf die zu exportierenden
     * Läufe, ohne schon CSV zu bauen - so bleibt der Delta-Abgleich dazwischenschaltbar.
     *
     * [allRounds] = false ist der initiale Fall: je Wettkampf die ERSTE gesetzte Runde - das, was
     * vor dem ersten Start ins Zeitnahme-System importiert wird. true (Delta) nimmt alle bereits
     * gesetzten Runden; welche davon exportiert werden, entscheidet danach der Feed-Abgleich.
     *
     * [skipByes] lässt Freilose weg - außer denen mit "muss gefahren werden" (bye_must_race), die
     * IMMER exportiert werden: Sie werden gefahren und brauchen ihre Welle im Zeitnahme-System.
     */
    fun eventStartlistPlan(
        eventId: UUID,
        allRounds: Boolean,
        skipByes: Boolean,
    ): App<ServiceError, List<BulkStartlistCompetition>> = KIO.comprehension {
        val competitions = !CompetitionMatchRepo.getForBulkStartlistExport(eventId).orDie()
        val byeByMatch = !MatchByeService.byeByMatch(eventId)

        competitions.traverse { (competitionId, identifier, raceUrl) ->
            KIO.comprehension {
                val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
                val sorted = sortRounds(setupRounds).filter { it.matches.isNotEmpty() }
                val rounds = if (allRounds) sorted else listOfNotNull(sorted.firstOrNull())

                // Nach Startzeit sortiert: `round.matches` kommt aus einem array_agg ohne
                // Ordnung - ohne die Sortierung wäre die Reihenfolge des Plans dem Zufall der
                // Aggregation überlassen.
                val matches = rounds.flatMap { round ->
                    round.matches
                        .filter { match ->
                            val bye = byeByMatch[match.competitionSetupMatch]
                            bye == null || bye.mustRace || !skipByes
                        }
                        .map { match ->
                            BulkStartlistMatch(
                                setupMatchId = match.competitionSetupMatch,
                                startTime = match.startTime,
                                roundName = round.setupRoundName,
                                matchTeamIds = match.teams.map { it.id },
                            )
                        }
                }.sortedWith(compareBy(nullsLast()) { it.startTime })

                KIO.ok(
                    BulkStartlistCompetition(
                        competitionId = competitionId,
                        identifier = identifier,
                        raceUrl = raceUrl,
                        matches = matches,
                    )
                )
            }
        }
    }

    /**
     * Baut aus dem Plan die Download-Datei.
     *
     * [feedsByUrl] != null ist der Delta-Modus: je Rennen die bereits geholten Feed-Zeilen -
     * exportiert wird nur, was dort fehlt ([RaceClockerAssignmentLogic.matchInFeed]). Ein
     * Wettkampf ohne angewähltes Rennen fällt im Delta komplett heraus: Es gibt kein Rennen, in
     * dem seine Wellen fehlen könnten, und keins, in das sie importiert würden.
     *
     * Getrennt vom Holen ([downloadEventStartlists]), damit der Bau gegen Fixture-Feeds prüfbar
     * ist - dasselbe Muster wie applyRaceClockerRows gegenüber updateMatchResultFromRaceClocker.
     */
    fun buildEventStartlists(
        plan: List<BulkStartlistCompetition>,
        fileType: EventStartlistFileType,
        feedsByUrl: Map<String, List<RaceClockerFeedRow>>? = null,
    ): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        val filtered = plan.map { competition ->
            if (feedsByUrl == null) {
                competition
            } else {
                val rows = competition.raceUrl?.let { feedsByUrl[it] }
                if (rows == null) {
                    competition.copy(matches = emptyList())
                } else {
                    competition.copy(
                        matches = competition.matches.filter {
                            !RaceClockerAssignmentLogic.matchInFeed(rows, it.matchTeamIds)
                        }
                    )
                }
            }
        }

        val fileNameDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

        when (fileType) {
            EventStartlistFileType.CSV -> {
                // Eine große Datei: nach Startzeit über alle Wettkämpfe sortiert - die Reihenfolge,
                // in der die Wellen gefahren werden. Kopfzeile nur einmal, wie beim Runden-Export.
                val out = ByteArrayOutputStream()
                var first = true
                !filtered.flatMap { it.matches }
                    .sortedWith(compareBy(nullsLast()) { it.startTime })
                    .traverse { m ->
                        KIO.comprehension {
                            val appended = !appendMatchCsv(m.setupMatchId, out, writeHeader = first)
                            if (appended != null) first = false
                            unit
                        }
                    }

                KIO.ok(
                    ApiResponse.File(
                        name = "startLists-$fileNameDate.csv",
                        bytes = out.toByteArray(),
                    )
                )
            }

            EventStartlistFileType.ZIP -> {
                // Eine CSV je Wettkampf, Dateinamen wie beim Runden-Export. Spannt ein Wettkampf im
                // Delta mehrere Runden auf, bleibt nur die Kennung - ein Rundenname wäre gelogen.
                val zipBytes = ByteArrayOutputStream()
                val zip = ZipOutputStream(zipBytes)

                !filtered.traverse { competition ->
                    KIO.comprehension {
                        val out = ByteArrayOutputStream()
                        var first = true
                        val exportedRounds = mutableSetOf<String>()
                        !competition.matches
                            .sortedWith(compareBy(nullsLast()) { it.startTime })
                            .traverse { m ->
                                KIO.comprehension {
                                    val appended = !appendMatchCsv(m.setupMatchId, out, writeHeader = first)
                                    if (appended != null) {
                                        first = false
                                        exportedRounds += m.roundName
                                    }
                                    unit
                                }
                            }
                        // Wettkämpfe ohne exportierten Lauf (keine Runde gesetzt, alles Freilose,
                        // im Delta vollständig vorhanden) bekommen keinen leeren ZIP-Eintrag.
                        if (exportedRounds.isEmpty()) return@comprehension unit

                        val entryName = exportedRounds.singleOrNull()
                            ?.let { "startList-${competition.identifier}-$it.csv" }
                            ?: "startList-${competition.identifier}.csv"
                        zip.putNextEntry(ZipEntry(entryName))
                        zip.write(out.toByteArray())
                        zip.closeEntry()
                        unit
                    }
                }

                zip.finish()
                KIO.ok(
                    ApiResponse.File(
                        name = "startLists-$fileNameDate.zip",
                        bytes = zipBytes.toByteArray(),
                    )
                )
            }
        }
    }

    /**
     * Startlisten-Sammelexport am Zeitplan-Tab: alles, was ins Zeitnahme-System importiert werden
     * muss, in einem Download - statt Wettkampf für Wettkampf über die Durchführungsseite.
     *
     * Das Holen der Feeds steht hier, der Bau in [buildEventStartlists] (Begründung dort). Im
     * Delta-Modus wird je angewähltem Rennen genau einmal geholt - dieselbe Fetch-Maschinerie wie
     * beim automatischen Abruf, samt Host-Allowlist. Ein nicht erreichbares Rennen reißt den
     * GANZEN Export mit einem strukturierten Fehler ([RaceClockerError.Unreachable]): Ein
     * Teilexport ohne Hinweis würde fehlende Wellen verschweigen, und verschwiegen fehlt am
     * Renntag genau die eine, auf die es ankommt.
     */
    suspend fun CallComprehensionScope.downloadEventStartlists(
        eventId: UUID,
        fileType: EventStartlistFileType,
        skipByes: Boolean,
        onlyMissingInRaceClocker: Boolean,
    ): App<ServiceError, ApiResponse.File> {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val plan = !eventStartlistPlan(eventId, allRounds = onlyMissingInRaceClocker, skipByes = skipByes)

        val feeds = if (onlyMissingInRaceClocker) {
            val fetched = mutableMapOf<String, List<RaceClockerFeedRow>>()
            for (rawUrl in plan.mapNotNull { it.raceUrl }.distinct()) {
                val url = !RaceClockerFeed.normalizeUrl(rawUrl)
                fetched[rawUrl] = !RaceClockerFeed.fetch(RaceClockerFeed.feedUrl(url))
            }
            fetched
        } else {
            null
        }

        return buildEventStartlists(plan, fileType, feeds)
    }

    /**
     * Schaltet "muss gefahren werden" an einem Freilos-Lauf um (competition_match.bye_must_race).
     *
     * Der Lauf gilt danach operativ als echtes Rennen: Startlisten-Exporte nehmen ihn mit, der
     * RaceClocker-Abruf und die Ergebniseingabe behandeln ihn normal, und Folgerunden-Automatik
     * wie Kette warten auf sein Beenden. Das Weiterkommen bleibt Freilos-Semantik - die eine
     * fahrende Mannschaft steigt unabhängig von Zeit und Platz auf. Das trägt die Auslosung
     * selbst: `createNewRound` setzt über die Plätze INNERHALB des Laufs, und in einem Lauf mit
     * genau einer fahrenden Mannschaft kann jedes Ergebnis nur Platz 1 ergeben - die gemessene
     * Zeit kann die Setzung also nicht verändern, sie läuft "außer Konkurrenz" in die Anzeige.
     *
     * Beim Einschalten wird ein bereits vergebener AUTOMATISCHER erster Platz zurückgenommen
     * (erkennbar an Platz ohne Zeit und ohne Ausscheidung): Mit gesetztem Platz wäre der Lauf für
     * Kette und Automatik schon "durch" (match_open = false), bevor er gefahren ist. Beim
     * Ausschalten wird er unter denselben Bedingungen wieder vergeben - ein Freilos der ersten
     * Runde, das durch eine Abmeldung entstand und nie einen automatischen Platz hatte, wird durch
     * das Hin- und Herschalten damit auf den Normalzustand mit automatischem Platz 1 gehoben
     * (bewusste Vereinfachung: der Unterschied ist dort nicht mehr rekonstruierbar).
     */
    fun updateByeMustRace(
        eventId: UUID,
        competitionId: UUID,
        matchId: UUID,
        userId: UUID,
        request: UpdateMatchByeMustRaceRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        !EventService.checkIsChallengeEvent(eventId).onTrueFail { CompetitionExecutionError.IsChallengeEvent }

        val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
        val round = setupRounds.find { r -> r.matches.any { it.competitionSetupMatch == matchId } }
            ?: return@comprehension KIO.fail(CompetitionExecutionError.MatchNotFound)
        val match = round.matches.first { it.competitionSetupMatch == matchId }

        // Nur ein Freilos kann "muss gefahren werden" - dieselbe Regel wie MatchStatusLogic.deriveBye:
        // nicht verpflichtende Runde, genau eine nicht als `out` mitgeführte Mannschaft.
        !KIO.failOn(round.required || match.teams.count { !it.out } != 1) { CompetitionExecutionError.MatchIsNoBye }

        // Ein beendeter Lauf ist entschieden - erst das Beenden zurücknehmen, dann umschalten.
        !KIO.failOn(match.finishedAt != null) { CompetitionExecutionError.MatchResultsLocked }

        val racingTeam = match.teams.single { !it.out }
        // Ein Platz ohne Zeit und ohne Ausscheidung ist der automatisch vergebene erste Platz -
        // ein gemessenes oder von Hand eingetragenes Ergebnis wird hier nie angefasst.
        val automaticPlaceOnly =
            racingTeam.timeString == null && !racingTeam.failed && racingTeam.penaltySeconds == null

        !CompetitionMatchRepo.update(matchId) {
            byeMustRace = request.mustRace
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        if (request.mustRace && racingTeam.place != null && automaticPlaceOnly) {
            !CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, racingTeam.competitionRegistration) {
                place = null
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie()
        }
        if (!request.mustRace && racingTeam.place == null && automaticPlaceOnly && match.startedAt == null) {
            !CompetitionMatchTeamRepo.updateByMatchAndRegistrationId(matchId, racingTeam.competitionRegistration) {
                place = 1
                updatedBy = userId
                updatedAt = LocalDateTime.now()
            }.orDie()
        }

        // Freilos-Zustand und automatischer Platz stehen in der Freilos-Anzeige der Boards.
        EventChangeMarker.bump(eventId)

        noData
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
        /**
         * Für den Runden-Export ([downloadRoundStartlist]): Nur die erste Partie schreibt die
         * Kopfzeile, die folgenden hängen nur Zeilen an - eine Datei, ein Header.
         */
        includeHeader: Boolean = config.noHeader != true,
    ): ByteArray {

        val bytes = ByteArrayOutputStream().use { out ->
            CSV.write(
                out,
                data.teams.sortedBy { it.startNumber },
                writeHeader = includeHeader,
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

                // Die Wellen-Name-Formatierung (Startzeit + Wettkampf + Name) MUSS mit
                // CompetitionMatchRepo.getForRaceClockerPull übereinstimmen (siehe WaveName) - sonst
                // greift dessen Fallback-Filter über den Wellen-Namen beim Ergebnis-Pull nicht mehr.
                optionalColumn(config.colMatchName) {
                    WaveName.format(
                        matchName = data.matchName,
                        startTime = data.startTime,
                        competitionIdentifier = data.competition.identifier,
                        competitionShortName = data.competition.shortName,
                    ) ?: ""
                }
                optionalColumn(config.colMatchStartTime) { idx ->
                    val offsetSeconds = idx * (data.startTimeOffset ?: 0)
                    data.startTime?.plusSeconds(offsetSeconds)
                        ?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        ?: ""
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