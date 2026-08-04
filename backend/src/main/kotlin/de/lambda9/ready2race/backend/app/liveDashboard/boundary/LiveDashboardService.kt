package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.EventScheduleLogic
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleChainService
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.liveDashboard.control.LiveDashboardRepo
import de.lambda9.ready2race.backend.app.liveDashboard.entity.*
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.singletonOrFallback
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.jooq.Record
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

object LiveDashboardService {

    fun getLiveDashboard(
        eventId: UUID,
        scope: LiveDashboardScope,
    ): App<LiveDashboardError, ApiResponse.ETagged<LiveDashboardDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
            }

            val matchRecords = !LiveDashboardRepo.getMatches(eventId).orDie()
            val teamRecords = !LiveDashboardRepo.getTeams(eventId).orDie()
            val requirementRecords = !LiveDashboardRepo.getEventRequirements(eventId).orDie()
            val checkRecords = !LiveDashboardRepo.getChecks(eventId).orDie()
            val invoiceRecords = !LiveDashboardRepo.getInvoicePaymentsByClub(eventId).orDie()
            val substitutionRecords = !SubstitutionRepo.getByEvent(eventId, null, Privilege.Scope.GLOBAL).orDie()

            val context = ParticipantContext(requirementRecords, checkRecords, substitutionRecords)

            val paidAtsByClub = invoiceRecords.groupBy(
                { it[INVOICE_FOR_EVENT_REGISTRATION.CLUB] },
                { it[INVOICE_FOR_EVENT_REGISTRATION.PAID_AT] },
            )

            val teamsByMatch = teamRecords.groupBy { it.get("match_id", UUID::class.java)!! }
            val now = LocalDateTime.now()

            // Resolves one team's registered crew against the round's substitutions and builds its DTO.
            fun buildTeamDto(
                registrationId: UUID,
                rows: List<Record>,
                startTime: LocalDateTime?,
            ): App<Nothing, LiveDashboardTeamDto> = KIO.comprehension {
                val first = rows.first()
                val clubId = first.get("club_id", UUID::class.java)
                val clubName = first.get("club_name", String::class.java)
                val teamName = first.get("team_name", String::class.java)

                val participants = !buildParticipants(rows, registrationId, startTime, context)

                KIO.ok(
                    LiveDashboardTeamDto(
                        teamId = registrationId,
                        teamName = teamName,
                        clubName = clubName,
                        actualClubName = singletonOrFallback(
                            participants.map { it.externalClubName }.toSet(),
                            first[EVENT.MIXED_TEAM_TERM],
                        ),
                        startNumber = first[COMPETITION_MATCH_TEAM.START_NUMBER],
                        place = first[COMPETITION_MATCH_TEAM.PLACE],
                        time = first[TIMECODE.TIME]?.let {
                            Timecode(
                                millis = it,
                                baseUnit = Timecode.BaseUnit.valueOf(first[TIMECODE.BASE_UNIT]!!),
                                millisecondPrecision = Timecode.MillisecondPrecision.valueOf(first[TIMECODE.MILLISECOND_PRECISION]!!),
                            ).toString()
                        },
                        failed = first[COMPETITION_MATCH_TEAM.FAILED] == true,
                        failedReason = first[COMPETITION_MATCH_TEAM.FAILED_REASON],
                        penaltySeconds = first[COMPETITION_MATCH_TEAM.PENALTY_SECONDS],
                        penaltyNote = first[COMPETITION_MATCH_TEAM.PENALTY_NOTE],
                        deregistered = first.get("deregistered", Boolean::class.java) == true,
                        deregisteredReason = first.get("deregistration_reason", String::class.java),
                        invoiceState = LiveDashboardLogic.deriveInvoiceState(
                            clubId?.let { paidAtsByClub[it] } ?: emptyList()
                        ),
                        // Die Personendaten selbst bleiben hier: sie sind der größte Posten im
                        // Poll und werden erst im Detail-Dialog gebraucht.
                        requirements = LiveDashboardLogic.summarizeRequirements(
                            participants.flatMap { it.requirements }
                        ),
                        substituted = participants.any { it.substitutedFor != null },
                    )
                )
            }

            fun buildMatchDto(match: Record): App<Nothing, LiveDashboardMatchDto> = KIO.comprehension {
                val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
                val startTime = match[COMPETITION_MATCH.START_TIME]
                val startedAt = match[COMPETITION_MATCH.STARTED_AT]
                val finishedAt = match[COMPETITION_MATCH.FINISHED_AT]
                val running = match[COMPETITION_MATCH.CURRENTLY_RUNNING] == true

                val teams = !(teamsByMatch[matchId] ?: emptyList())
                    .groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION]!! }
                    .toList()
                    .traverse { (registrationId, rows) -> buildTeamDto(registrationId, rows, startTime) }
                    .map { list -> list.sortedWith(compareBy(nullsLast()) { it.startNumber }) }

                KIO.ok(
                    LiveDashboardMatchDto(
                        matchId = matchId,
                        state = LiveDashboardLogic.deriveMatchState(running, startTime, finishedAt, teams.map { LiveDashboardLogic.teamHasResult(it.place, it.failed, it.deregistered) }),
                        competitionId = match.get("competition_id", UUID::class.java)!!,
                        competitionName = match.get("competition_name", String::class.java) ?: "",
                        categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                        roundName = match.get("round_name", String::class.java),
                        matchName = match.get("match_name", String::class.java),
                        executionOrder = match[COMPETITION_SETUP_MATCH.EXECUTION_ORDER] ?: 0,
                        startTime = startTime,
                        startedAt = startedAt,
                        currentlyRunning = running,
                        elapsedMinutes = startedAt?.let { Duration.between(it, now).toMinutes().coerceAtLeast(0) },
                        teams = teams,
                    )
                )
            }

            val matches = !matchRecords.traverse { match -> buildMatchDto(match) }
            val pendingSlots = !getPendingSlots(eventId, matches.map { it.matchId }.toSet())

            KIO.ok(
                ApiResponse.ETagged(
                    LiveDashboardDto(
                        matches = LiveDashboardLogic.selectForScope(matches, scope),
                        // Unabhängig vom Scope: auch im LIVE-Ausschnitt soll sichtbar bleiben, was
                        // als nächstes ansteht, auch wenn die Runde noch nicht erzeugt ist.
                        pendingSlots = pendingSlots,
                    )
                )
            )
        }

    /**
     * WAITING- und FREE-Slots des Events als Platzhalter (Task 14, erweitert um Programmpunkte) -
     * gemeinsam nach Startzeit sortiert. Die "nur WAITING zählt"-Regel für Lauf-Platzhalter steckt
     * gemeinsam mit der Athleten-Anzeige in [EventScheduleLogic.pendingSlotOrNull]: SKIPPED, FREE,
     * LINKED und OBSOLETE liefern dort keinen Eintrag - LINKED ist bereits ein echter Lauf und
     * steckt in [matches], die anderen sind kein Kandidat für einen künftigen Lauf. FREE-Slots
     * (Programmpunkte) kommen zusätzlich über [EventScheduleLogic.freeSlotOrNull] hinzu - anders
     * als bei der Athleten-Anzeige/dem Kiosk ist das hier gewollt: Schiedsrichter sollen auch
     * Pausen im Ablauf sehen, öffentliche Boards bewusst nicht.
     */
    private fun getPendingSlots(eventId: UUID, matchIds: Set<UUID>): App<Nothing, List<PendingSlotDto>> = KIO.comprehension {
        val slotRecords = !EventScheduleRepo.getSlots(eventId).orDie()

        val waiting = slotRecords.mapNotNull { r ->
            EventScheduleLogic.pendingSlotOrNull(
                slotId = r[EVENT_SCHEDULE_SLOT.ID]!!,
                setupMatchId = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH],
                startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                competitionId = r.get("competition_id", UUID::class.java),
                competitionName = r.get("competition_name", String::class.java),
                roundName = r.get("round_name", String::class.java),
                matchName = r.get("match_name", String::class.java),
                skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                roundMaterialized = r.get("round_materialized", Boolean::class.java) == true,
                matchExists = r.get("match_exists", Boolean::class.java) == true,
            )
        }
            // Zwei getrennte Reads — wenn zwischen ihnen eine Runde entsteht oder gelöscht wird,
            // könnte derselbe Lauf doppelt auftauchen; echte Einträge gewinnen.
            .filterNot { it.setupMatchId in matchIds }
            .map { slot ->
                PendingSlotDto(
                    slotId = slot.slotId,
                    startTime = slot.startTime,
                    name = null,
                    competitionName = slot.competitionName,
                    roundName = slot.roundName,
                    matchName = slot.matchName,
                )
            }

        val free = slotRecords.mapNotNull { r ->
            EventScheduleLogic.freeSlotOrNull(
                slotId = r[EVENT_SCHEDULE_SLOT.ID]!!,
                isFree = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null,
                name = r[EVENT_SCHEDULE_SLOT.NAME],
                startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
            )
        }.map { slot ->
            PendingSlotDto(
                slotId = slot.slotId,
                startTime = slot.startTime,
                name = slot.name,
                competitionName = null,
                roundName = null,
                matchName = null,
            )
        }

        KIO.ok((waiting + free).sortedBy { it.startTime })
    }

    /**
     * Personendaten einer Mannschaft für den Detail-Dialog: Aufstellung, Ummeldungen und die
     * Teilnahmebedingungen mit allem, was die Liste bewusst nicht mehr mitschickt.
     */
    fun getTeamDetail(
        eventId: UUID,
        matchId: UUID,
        teamId: UUID,
    ): App<LiveDashboardError, ApiResponse.Dto<LiveDashboardTeamDetailDto>> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        val teamRecords = !LiveDashboardRepo.getTeams(eventId, matchId, teamId).orDie()
        if (teamRecords.isEmpty()) {
            return@comprehension KIO.fail(LiveDashboardError.TeamNotFound(teamId))
        }

        val startTime = !LiveDashboardRepo.getMatchStartTime(matchId).orDie()
        val requirementRecords = !LiveDashboardRepo.getEventRequirements(eventId).orDie()
        val checkRecords = !LiveDashboardRepo.getChecks(eventId).orDie()
        val substitutionRecords = !SubstitutionRepo.getByEvent(eventId, null, Privilege.Scope.GLOBAL).orDie()

        val participants = !buildParticipants(
            rows = teamRecords,
            registrationId = teamId,
            startTime = startTime,
            context = ParticipantContext(requirementRecords, checkRecords, substitutionRecords),
        )

        KIO.ok(ApiResponse.Dto(LiveDashboardTeamDetailDto(teamId, participants)))
    }
    /**
     * Erklärt einen Lauf für beendet und zieht die nächsten nach: aktiv sind danach die Läufe mit
     * der frühesten noch offenen Startzeit — meist einer, bei parallelen Starts mehrere.
     *
     * Damit hält sich das Feld ohne Zutun aktuell: Schiedsrichter sehen den Lauf, den sie gerade
     * vorbereiten oder abnehmen, und geben ihn nach der Ergebniskontrolle selbst frei.
     */
    fun finishMatch(
        eventId: UUID,
        matchId: UUID,
        userId: UUID,
        openResults: OpenResultHandling? = null,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        // Sammelentscheidung für die Boote ohne Ergebnis, bevor der Lauf aus der Ansicht geht.
        if (openResults != null) {
            !LiveDashboardRepo.markOpenTeamsFailed(matchId, openResults.name, userId).orDie()
        }

        // Die Kette läuft vorwärts: aktiviert werden nur Läufe, die später starten als der eben
        // beendete. Sonst würde ein ohne vollständige Ergebnisse freigegebener Lauf sich selbst
        // wieder einreihen — zurückholen geht bewusst nur von Hand.
        //
        // Ist die Automatik für die Veranstaltung aus, beendet der Aufruf nur diesen Lauf. Das ist
        // die sichere Wahl, solange der Zeitplan Lücken hat: Startzeiten stehen erst fest, wenn die
        // Läufe einer Runde gesetzt sind, und die Kette würde sonst den falschen Lauf greifen.
        val chainEnabled = !EventRepo.getAutoActivateNextMatch(eventId).orDie()

        !setRunning(matchId, false, userId)
        !CompetitionMatchRepo.update(matchId) {
            finishedAt = LocalDateTime.now()
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        if (chainEnabled) {
            val slotTime = !EventScheduleRepo.getSlotBySetupMatch(matchId).orDie()
            // Bei teilweise gepflegtem Zeitstrahl entscheidet jeder Lauf für sich — ein Lauf ohne
            // Slot nutzt die Legacy-Logik, auch wenn andere Läufe Slots haben.
            if (slotTime != null) {
                // Zeitstrahl-Modus: der Kette entlang der Slots folgen, an wartenden Slots geduldig
                // sein (createNewRound stößt die Kette dann später wieder an).
                !ScheduleChainService.decideAndActivate(eventId, after = slotTime, userId)
            } else {
                // Legacy: Events ohne Zeitstrahl behalten das bisherige Verhalten.
                val finishedStart = !LiveDashboardRepo.getMatchStartTime(matchId).orDie()
                val candidates = (!LiveDashboardRepo.getActivationCandidates(eventId).orDie())
                    .filter { candidate ->
                        val start = candidate[COMPETITION_MATCH.START_TIME]
                        finishedStart == null || (start != null && start > finishedStart)
                    }
                !activateNext(candidates, userId)
            }
        }

        noData
    }

    /** Manuelles Übersteuern, falls zu viele oder zu wenige Läufe aktiv sind. */
    fun setMatchRunning(
        eventId: UUID,
        matchId: UUID,
        running: Boolean,
        userId: UUID,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        !setRunning(matchId, running, userId)

        noData
    }

    /**
     * Markiert den echten Start eines Laufs — getrennt von der geplanten Startzeit. Idempotent:
     * ein zweiter Aufruf verschiebt den Zeitstempel nicht mehr, er ist nur beim ersten Mal gesetzt.
     * Zugleich geht der Lauf auf "aktiv", da "gestartet" ohne "laufend" keinen Sinn ergibt.
     */
    fun markMatchStarted(
        eventId: UUID,
        matchId: UUID,
        userId: UUID,
    ): App<LiveDashboardError, ApiResponse.NoData> = KIO.comprehension {
        val exists = !EventRepo.exists(eventId).orDie()
        if (!exists) {
            return@comprehension KIO.fail(LiveDashboardError.EventNotFound(eventId))
        }

        !CompetitionMatchRepo.update(matchId) {
            if (startedAt == null) {
                startedAt = LocalDateTime.now()
            }
            currentlyRunning = true
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie()

        noData
    }

    /**
     * Die veranstaltungsweiten Daten, die für jede Mannschaft gleich sind — einmal aufbereitet
     * statt je Mannschaft neu gruppiert.
     */
    private class ParticipantContext(
        requirementRecords: List<Record>,
        checkRecords: List<Record>,
        substitutionRecords: List<SubstitutionViewRecord>,
    ) {
        /** requirement id -> assigned named participants (null element = global assignment) */
        val requirementAssignments = requirementRecords.groupBy(
            { it[PARTICIPANT_REQUIREMENT.ID]!! },
            { it[EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT] },
        )

        val requirementInfos = requirementRecords.distinctBy { it[PARTICIPANT_REQUIREMENT.ID] }

        val checksByKey = checkRecords.associateBy {
            it[PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT]!! to
                it[PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT_REQUIREMENT]!!
        }

        /** (round, registration) -> substitutions applying to that team in that round, ordered */
        val substitutionsByKey = substitutionRecords
            .groupBy { it.competitionSetupRoundId!! to it.competitionRegistrationId!! }
            .mapValues { (_, subs) -> subs.sortedBy { it.orderForRound } }
    }

    /**
     * Löst die gemeldete Aufstellung einer Mannschaft gegen die Ummeldungen der Runde auf und
     * hängt jeder Person ihre Teilnahmebedingungen an.
     */
    private fun buildParticipants(
        rows: List<Record>,
        registrationId: UUID,
        startTime: LocalDateTime?,
        context: ParticipantContext,
    ): App<Nothing, List<LiveDashboardParticipantDto>> = KIO.comprehension {
        val first = rows.first()
        val clubId = first.get("club_id", UUID::class.java)
        val clubName = first.get("club_name", String::class.java)
        val teamName = first.get("team_name", String::class.java)
        val roundId = first.get("round_id", UUID::class.java)!!

        val registered = rows.mapNotNull { row ->
            val participantId = row.get("participant_id", UUID::class.java)
            val namedParticipantId = row.get("named_participant_id", UUID::class.java)
            if (participantId == null || namedParticipantId == null) {
                null
            } else {
                ParticipantForExecutionDto(
                    id = participantId,
                    namedParticipantId = namedParticipantId,
                    namedParticipantName = row.get("named_role", String::class.java) ?: "",
                    firstName = row[PARTICIPANT.FIRSTNAME] ?: "",
                    lastName = row[PARTICIPANT.LASTNAME] ?: "",
                    year = row[PARTICIPANT.YEAR]!!,
                    gender = row[PARTICIPANT.GENDER]!!,
                    clubId = clubId!!,
                    clubName = clubName ?: "",
                    competitionRegistrationId = registrationId,
                    competitionRegistrationName = teamName,
                    external = row[PARTICIPANT.EXTERNAL],
                    externalClubName = row[PARTICIPANT.EXTERNAL_CLUB_NAME],
                )
            }
        }.distinctBy { it.id to it.namedParticipantId }

        val subs = context.substitutionsByKey[roundId to registrationId] ?: emptyList()

        // Post-substitution crew: this is the crew that actually starts, incl. taken-over roles.
        val resolved = !CompetitionExecutionService.getActuallyParticipatingParticipants(registered, subs)

        // Who was substituted in for whom, so the dashboard can show the change
        // instead of silently presenting a different crew than the one that was entered.
        val substitutedForByParticipant = subs
            .filter { it.participantIn != null }
            .associateBy({ it.participantIn!!.id!! }, { it })

        KIO.ok(
            resolved.map { p ->
                val substitution = substitutedForByParticipant[p.id]
                val replaced = substitution?.participantOut

                val requirements = context.requirementInfos
                    .filter { req ->
                        LiveDashboardLogic.requirementApplies(
                            context.requirementAssignments[req[PARTICIPANT_REQUIREMENT.ID]!!] ?: emptyList(),
                            p.namedParticipantId,
                        )
                    }
                    .map { req ->
                        val check = context.checksByKey[p.id to req[PARTICIPANT_REQUIREMENT.ID]!!]
                        LiveDashboardRequirementStatusDto(
                            requirementId = req[PARTICIPANT_REQUIREMENT.ID]!!,
                            name = req[PARTICIPANT_REQUIREMENT.NAME]!!,
                            description = req[PARTICIPANT_REQUIREMENT.DESCRIPTION],
                            optional = req[PARTICIPANT_REQUIREMENT.OPTIONAL]!!,
                            checked = check != null,
                            checkedAt = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.CREATED_AT),
                            note = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.NOTE),
                            timeCheck = LiveDashboardLogic.computeTimeCheck(
                                startTime = startTime,
                                checkedAt = check?.get(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.CREATED_AT),
                                earliestMinutesBefore = req[PARTICIPANT_REQUIREMENT.CHECK_EARLIEST_MINUTES_BEFORE],
                                latestMinutesBefore = req[PARTICIPANT_REQUIREMENT.CHECK_LATEST_MINUTES_BEFORE],
                            ),
                        )
                    }

                LiveDashboardParticipantDto(
                    participantId = p.id,
                    firstName = p.firstName,
                    lastName = p.lastName,
                    namedRole = p.namedParticipantName,
                    year = p.year,
                    gender = p.gender.name,
                    externalClubName = p.externalClubName,
                    substitutedFor = replaced?.let { "${it.firstname} ${it.lastname}" },
                    substitutionReason = substitution?.reason,
                    requirements = requirements,
                )
            }
        )
    }

    private fun setRunning(matchId: UUID, running: Boolean, userId: UUID): App<Nothing, Unit> =
        CompetitionMatchRepo.update(matchId) {
            currentlyRunning = running
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }.orDie().map { }

    private fun activateNext(candidates: List<Record>, userId: UUID): App<Nothing, Unit> =
        KIO.comprehension {
            val nextStart = candidates.firstOrNull()?.get(COMPETITION_MATCH.START_TIME)
                ?: return@comprehension KIO.unit

            // Alle Läufe derselben Startzeit gemeinsam aktivieren: parallele Starts gehören zusammen.
            !candidates
                .filter { it[COMPETITION_MATCH.START_TIME] == nextStart }
                .traverse { setRunning(it[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!, true, userId) }

            KIO.unit
        }

}
