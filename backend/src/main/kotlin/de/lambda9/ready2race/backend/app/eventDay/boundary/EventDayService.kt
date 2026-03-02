package de.lambda9.ready2race.backend.app.eventDay.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.boundary.EventService
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayHasCompetitionRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayRepo
import de.lambda9.ready2race.backend.app.eventDay.control.eventDayDto
import de.lambda9.ready2race.backend.app.eventDay.control.toRecord
import de.lambda9.ready2race.backend.app.eventDay.entity.*
import de.lambda9.ready2race.backend.app.competition.control.CompetitionRepo
import de.lambda9.ready2race.backend.app.competitionDeregistration.control.CompetitionDeregistrationRepo
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.getCurrentAndNextRound
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.getSeedingList
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.sortRounds
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionMatchTeamWithRegistration
import de.lambda9.ready2race.backend.app.competitionExecution.entity.CompetitionSetupRoundWithMatches
import de.lambda9.ready2race.backend.app.competitionRegistration.control.CompetitionRegistrationRepo
import de.lambda9.ready2race.backend.app.competitionSetup.boundary.CompetitionSetupService
import de.lambda9.ready2race.backend.app.competitionSetup.control.CompetitionSetupParticipantRepo
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupError
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventDay.control.EventDayWithMatchesRepo
import de.lambda9.ready2race.backend.app.eventDay.control.TimeslotRepo
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayHasCompetitionRecord
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.calls.responses.fileResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotRecord
import de.lambda9.ready2race.backend.file.File
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.document
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.filter
import kotlin.collections.isNotEmpty

object EventDayService {

    fun addEventDay(
        request: EventDayRequest,
        userId: UUID,
        eventId: UUID
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie()
            .onNullFail { EventError.NotFound }
        !KIO.failOn(event.challengeEvent == true) { EventDayError.IsChallengeEvent }

        val record = !request.toRecord(userId, eventId)
        val id = !EventDayRepo.create(record).orDie()
        KIO.ok(ApiResponse.Created(id))
    }

    fun pageByEvent(
        eventId: UUID,
        params: PaginationParameters<EventDaySort>,
        competitionId: UUID?,
        scope: Privilege.Scope?
    ): App<ServiceError, ApiResponse.Page<EventDayDto, EventDaySort>> = KIO.comprehension {

        !EventService.checkEventExisting(eventId)

        val total =
            if (competitionId == null) !EventDayRepo.countByEvent(eventId, params.search, scope).orDie()
            else !EventDayRepo.countByEventAndCompetition(eventId, competitionId, params.search).orDie()

        val page =
            if (competitionId == null) !EventDayRepo.pageByEvent(eventId, params, scope).orDie()
            else !EventDayRepo.pageByEventAndCompetition(eventId, competitionId, params).orDie()

        page.traverse { it.eventDayDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun getEventDay(
        eventDayId: UUID,
        scope: Privilege.Scope?
    ): App<EventDayError, ApiResponse> = KIO.comprehension {
        val eventDay =
            !EventDayRepo.getEventDay(eventDayId, scope).orDie().onNullFail { EventDayError.EventDayNotFound }
        eventDay.eventDayDto().map { ApiResponse.Dto(it) }
    }

    fun updateEventDay(
        request: EventDayRequest,
        userId: UUID,
        eventDayId: UUID,
    ): App<EventDayError, ApiResponse.NoData> =
        EventDayRepo.update(eventDayId) {
            date = request.date
            name = request.name
            description = request.description
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()
            .onNullFail { EventDayError.EventDayNotFound }
            .map { ApiResponse.NoData }

    fun deleteEvent(
        id: UUID
    ): App<EventDayError, ApiResponse.NoData> = KIO.comprehension {
        val deleted = !EventDayRepo.delete(id).orDie()

        if (deleted < 1) {
            KIO.fail(EventDayError.EventDayNotFound)
        } else {
            noData
        }
    }

    fun updateEventDayHasCompetition(
        request: AssignCompetitionsToDayRequest,
        userId: UUID,
        eventDayId: UUID
    ): App<EventDayError, ApiResponse.NoData> = KIO.comprehension {

        val eventDayExists = !EventDayRepo.exists(eventDayId).orDie()
        if (!eventDayExists) KIO.fail(EventDayError.EventDayNotFound)

        val unknownCompetitions = !CompetitionRepo.findUnknown(request.competitions).orDie()
        if (unknownCompetitions.isNotEmpty()) KIO.fail(EventDayError.CompetitionsNotFound(unknownCompetitions))

        !EventDayHasCompetitionRepo.deleteByEventDay(eventDayId).orDie()
        !EventDayHasCompetitionRepo.create(request.competitions.map {
            EventDayHasCompetitionRecord(
                eventDay = eventDayId,
                competition = it,
                createdAt = LocalDateTime.now(),
                createdBy = userId
            )
        }).orDie()

        noData
    }

    fun getCompetitionsForEventDay(
        eventDayId: UUID
    ): App<ServiceError, ApiResponse.ListDto<EventDayScheduleCompetitionDataDto>> = KIO.comprehension {
        !EventDayRepo.exists(eventDayId).orDie().onFalseFail { EventDayError.EventDayNotFound }
        val competitions = !EventDayWithMatchesRepo.selectByEventDayId(eventDayId).orDie()
        val dto = competitions.map { competition ->
            val competitionId = competition.competitionId!!
            val setupRounds = !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)
            val matchCounts = !countMatchesToFinal(competitionId)
            val roundInfosById = matchCounts.perRound.associateBy { it.setupRoundId }
            EventDayScheduleCompetitionDataDto(
                eventDayId = competition.id!!,
                competitionName = competition.competitionName!!,
                competitionId = competitionId,
                matchDuration = competition.matchDuration,
                matchGapsDuration = competition.matchGapsDuration,
                matchCount = matchCounts.totalMatches,
                rounds = sortRounds(setupRounds).map { round ->
                    val roundInfo = roundInfosById[round.setupRoundId]
                    val roundCount = roundInfo?.countedMatches ?: 0
                    val setupMatchById = round.setupMatches.associateBy { it.id }
                    val occurringMatches = (roundInfo?.occurringSetupMatchIds ?: emptyList())
                        .mapNotNull { setupMatchById[it] }
                    EventDayScheduleCompetitionRoundDataDto(
                        roundName = round.setupRoundName,
                        roundId = round.setupRoundId,
                        matchCount = roundCount,
                        matches = occurringMatches.map { match ->
                            EventDayScheduleCompetitionMatchDataDto(
                                matchName = match.name ?: "",
                                matchId = match.id
                            )
                        }
                    )
                },
            )
        }
        KIO.ok(ApiResponse.ListDto(dto))
    }

    data class MatchCountRoundInfo(
        val setupRoundId: UUID,
        val required: Boolean,
        val createdMatches: Int,
        val countedMatches: Int,
        val occurringSetupMatchIds: List<UUID>,
        val teamsInRoundTotal: Int,
        val teamsAdvancingTotal: Int,
        val roundName: String,
    )

    data class MatchCountSummary(
        val totalMatches: Int,
        val perRound: List<MatchCountRoundInfo>,
    )

    fun countMatchesToFinal(
        competitionId: UUID,
    ): App<CompetitionSetupError, MatchCountSummary> = KIO.comprehension {

        val setupRounds: List<CompetitionSetupRoundWithMatches> =
            !CompetitionSetupService.getSetupRoundsWithMatches(competitionId)

        if (setupRounds.isEmpty()) {
            return@comprehension KIO.ok(MatchCountSummary(0, emptyList()))
        }

        val roundById = setupRounds.associateBy { it.setupRoundId }

        // wie in createNewRound: bestimmt aktuelle + nächste Runde anhand setupRounds (inkl. DB-matches)
        val (currentRoundMaybe, nextRoundMaybe) = getCurrentAndNextRound(setupRounds)

        // Passe ggf. Typ/Felder an, falls dein Team-DTO anders heißt
        fun isActive(team: CompetitionMatchTeamWithRegistration): Boolean =
            !team.out && !team.deregistered && !team.failed

        // perRound + total
        val perRound = mutableListOf<MatchCountRoundInfo>()
        var totalCounted = 0

        fun addRoundInfo(
            round: CompetitionSetupRoundWithMatches,
            setupMatchesSorted: List<CompetitionSetupMatchRecord>,
            activeTeamsPerMatch: Map<UUID, Int>,
            teamsAdvancingTotal: Int
        ) {
            val required = round.required

            val createdMatches = setupMatchesSorted.count { sm -> (activeTeamsPerMatch[sm.id] ?: 0) > 0 }

            val occurringSetupMatchIds = setupMatchesSorted
                .filter { sm -> (activeTeamsPerMatch[sm.id] ?: 0) > 0 } // nur wenn überhaupt Teams drin sind
                .filter { sm ->
                    val active = activeTeamsPerMatch[sm.id] ?: 0
                    required || active > 1
                }.map { it.id }
            val countedMatches = occurringSetupMatchIds.size

            totalCounted += countedMatches

            perRound += MatchCountRoundInfo(
                setupRoundId = round.setupRoundId,
                required = required,
                createdMatches = createdMatches,
                countedMatches = countedMatches,
                occurringSetupMatchIds = occurringSetupMatchIds,
                teamsInRoundTotal = activeTeamsPerMatch.values.sum(),
                teamsAdvancingTotal = teamsAdvancingTotal,
                roundName = round.setupRoundName,
            )
        }

        fun advanceToNextRound(
            round: CompetitionSetupRoundWithMatches,
            nextRound: CompetitionSetupRoundWithMatches,
            activeTeamsPerMatch: Map<UUID, Int>,
        ): Map<UUID, Int> {

            val currentSetupMatches = round.setupMatches.sortedBy { it.weighting }
            val nextSetupMatches = nextRound.setupMatches.sortedBy { it.weighting }

            val currentMatchTeamCounts: List<Int?> =
                currentSetupMatches.map { sm -> (activeTeamsPerMatch[sm.id] ?: 0) }

            val nextRoundSlots = nextSetupMatches.sumOf { it.teams ?: 0 }

            val outcomes: List<List<Int>> = getSeedingList(
                currentMatchTeamCounts,
                nextRoundSlots
            )

            val nextParticipants: List<CompetitionSetupParticipantRecord> =
                !CompetitionSetupParticipantRepo.get(nextSetupMatches.map { it.id }).orDie()

            val nextParticipantsWithMatch =
                nextParticipants.filter { it.competitionSetupMatch != null }

            val participantBySeed: Map<Int, CompetitionSetupParticipantRecord> =
                nextParticipantsWithMatch.associateBy { it.seed }

            val nextActiveTeamsCountByMatchId = mutableMapOf<UUID, Int>()

            // Wir haben nur Counts, also iterieren wir "TeamIdx" synthetisch 0..count-1
            currentSetupMatches.forEachIndexed { matchIdx, sm ->
                val teamsInThisMatch = currentMatchTeamCounts.getOrNull(matchIdx) ?: 0
                if (teamsInThisMatch <= 0) return@forEachIndexed

                for (teamIdx in 0 until teamsInThisMatch) {
                    val outcomeSeed = outcomes.getOrNull(matchIdx)?.getOrNull(teamIdx) ?: continue
                    val participant = participantBySeed[outcomeSeed] ?: continue

                    val targetSetupMatchId = participant.competitionSetupMatch!!
                    nextActiveTeamsCountByMatchId[targetSetupMatchId] =
                        (nextActiveTeamsCountByMatchId[targetSetupMatchId] ?: 0) + 1
                }
            }

            return nextActiveTeamsCountByMatchId
        }

        // -------------------------
        // Startzustand bestimmen:
        // - Wenn currentRound == null: wir sind "vor der ersten Runde" => wie First-Round-Block zählen + dann weiter
        // - Sonst: currentRound aus DB zählen + dann weiter
        // -------------------------

        var currentRound: CompetitionSetupRoundWithMatches?
        var nextRound: CompetitionSetupRoundWithMatches?

        // activeTeamsPerMatch für "currentRound"
        var activeTeamsPerMatch: Map<UUID, Int>

        if (currentRoundMaybe == null) {
            // First round "trocken" wie createNewRound (nur aktive Registrations)
            nextRound = nextRoundMaybe
            if (nextRound == null) {
                return@comprehension KIO.ok(MatchCountSummary(0, emptyList()))
            }

            val registrations = !CompetitionRegistrationRepo.getByCompetitionId(competitionId).orDie()
            val deregisteredIds: Set<UUID> =
                (!CompetitionDeregistrationRepo.getByRegistrations(registrations.map { it.id }).orDie())
                    .map { it.competitionRegistration }
                    .toSet()

            val startActiveTeamCount = registrations.count { r ->
                r.teamNumber != null && !deregisteredIds.contains(r.id)
            }

            val setupMatches = nextRound!!.setupMatches.sortedBy { it.weighting }
            val seeding = getSeedingList(setupMatches.map { it.teams }, startActiveTeamCount)

            val assignedByMatchId = mutableMapOf<UUID, Int>()
            setupMatches.forEachIndexed { idx, sm ->
                assignedByMatchId[sm.id] = seeding.getOrNull(idx)?.count { it <= startActiveTeamCount } ?: 0
            }

            // Runde "nextRound" ist dann die erste, die gezählt wird
            val teamsAdvancingPlaceholder = 0
            addRoundInfo(
                round = nextRound!!,
                setupMatchesSorted = setupMatches,
                activeTeamsPerMatch = assignedByMatchId,
                teamsAdvancingTotal = teamsAdvancingPlaceholder
            )

            currentRound = nextRound
            nextRound = currentRound!!.nextRound?.let { roundById[it] }
            activeTeamsPerMatch = assignedByMatchId
        } else {
            // DB-Stand: currentRound exists
            currentRound = currentRoundMaybe
            nextRound = nextRoundMaybe

            val setupMatches = currentRound!!.setupMatches.sortedBy { it.weighting }
            val matchBySetupMatchId = currentRound!!.matches.associateBy { it.competitionSetupMatch }

            // aktive Teams pro setupMatch aus DB
            activeTeamsPerMatch = setupMatches.associate { sm ->
                val match = matchBySetupMatchId[sm.id]
                val active = match?.teams?.count { isActive(it) } ?: 0
                sm.id to active
            }

            // Wir zählen die CURRENT Runde aus DB (jetzt Stand)
            // teamsAdvancingTotal füllen wir gleich im nächsten Schritt (nach advanceToNextRound)
            addRoundInfo(
                round = currentRound!!,
                setupMatchesSorted = setupMatches,
                activeTeamsPerMatch = activeTeamsPerMatch,
                teamsAdvancingTotal = 0
            )
        }

        // -------------------------
        // Ab jetzt bis Finale “weiter wie createNewRound” simulieren
        // -------------------------
        while (currentRound != null && nextRound != null) {

            val nextActiveTeams = advanceToNextRound(
                round = currentRound!!,
                nextRound = nextRound!!,
                activeTeamsPerMatch = activeTeamsPerMatch
            )

            // teamsAdvancingTotal in der vorherigen perRound-Zeile nachtragen
            run {
                val li = perRound.lastIndex
                if (li >= 0) {
                    val prev = perRound[li]
                    perRound[li] = prev.copy(teamsAdvancingTotal = nextActiveTeams.values.sum())
                }
            }

            // nächste Runde zählen (simuliert)
            val nextSetupMatchesSorted = nextRound!!.setupMatches.sortedBy { it.weighting }
            addRoundInfo(
                round = nextRound!!,
                setupMatchesSorted = nextSetupMatchesSorted,
                activeTeamsPerMatch = nextActiveTeams,
                teamsAdvancingTotal = 0
            )

            // weiter schieben
            currentRound = nextRound
            activeTeamsPerMatch = nextActiveTeams
            nextRound = currentRound!!.nextRound?.let { roundById[it] }

            // Abbruch, wenn keine Teams mehr
            if (activeTeamsPerMatch.values.sum() <= 0) break
        }

        KIO.ok(MatchCountSummary(totalMatches = totalCounted, perRound = perRound))
    }

    fun downloadEventDaySchedulePdf(eventDay: UUID): App<ServiceError, ApiResponse.File> = KIO.comprehension {
        generateEventDaySchedulePdf(eventDay).fileResponse()
    }

    fun generateEventDaySchedulePdf(eventDayId: UUID)
    : App<ServiceError, File>
    = KIO.comprehension {
        val eventDay = !(!EventDayRepo.getEventDay(eventDayId, null).orDie()
            .onNullFail { EventDayError.EventDayNotFound }).eventDayDto()
        val event = !EventRepo.get(eventDay.event).orDie()
            .onNullFail { EventError.NotFound }

        val timeslots = (!TimeslotRepo.getByEventDay(eventDayId).orDie()).sortedBy { it.startTime }
        val bytes = buildPdf(event.name, listOf(Pair(eventDay, timeslots)))
        KIO.ok(
            File(
                name = "schedule-${event.name}_${eventDay.date}.pdf",
                bytes = bytes,
            )
        )
    }

    fun buildPdf(eventName: String, data: List<Pair<EventDayDto, List<TimeslotRecord>>>, ): ByteArray {
        val doc = document(null) {
            page {
                block {
                    text(
                        fontSize = 14f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        "Event Zeitplan"
                    }
                    text(
                        fontSize = 10f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        "Event Shedule"
                    }
                    text(
                        fontSize = 14f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        ""
                    }
                    text(
                        fontSize = 14f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        eventName
                    }
                    text(
                        fontSize = 14f,
                        fontStyle = FontStyle.BOLD,
                        centered = true,
                    ){
                        ""
                    }
                }
                data.forEach {
                    val firstTimeslot = it.second.firstOrNull()
                    val rest = if (firstTimeslot != null) it.second.drop(1) else it.second
                    fun displayDay() {
                        block {
                            text(
                                fontStyle = FontStyle.BOLD
                            ){
                                "${it.first.date}" + (if (it.first.name != null) ":  ${it.first.name}"  else "")
                            }
                            if (it.first.description == null) {
                                text(fontSize = 8f){
                                    "Keine Beschreibung"
                                }
                                text(fontSize = 6f,
                                    newLine = false){
                                    " / No description"
                                }
                            } else {
                                text { it.first.description!! }
                            }
                            text { "" }
                        }
                    }
                    fun timeslotDisplay(timeslot: TimeslotRecord) = block (
                        keepTogether = true,
                        padding = Padding(left = 8F),
                    ) {
                        text { timeslot.name }
                        text { "${timeslot.startTime} - ${timeslot.startTime}" }
                        if (timeslot.description == null) {
                            text(fontSize = 8f){
                                "Keine Beschreibung"
                            }
                            text(fontSize = 6f,
                                newLine = false){
                                " / No description"
                            }
                        } else {
                            text { timeslot.description!! }
                        }
                        text { "" }
                    }
                    block(
                        keepTogether = true
                    ) {
                        displayDay()
                        if (firstTimeslot != null) {
                            timeslotDisplay(firstTimeslot)
                        } else {
                            block (padding = Padding(left = 8F)) {
                                text(fontSize = 8f){
                                    "Keine Zeitplanung vorhanden"
                                }
                                text(fontSize = 6f,
                                    newLine = false){
                                    " / No schedule available"
                                }
                                text { "" }
                            }
                        }
                    }
                    rest.forEach { timeslot ->
                        timeslotDisplay(timeslot)
                    }
                }
            }
        }
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()

        val bytes = out.toByteArray()
        out.close()

        return bytes
    }
}
