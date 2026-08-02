package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.liveDashboard.control.LiveDashboardRepo
import de.lambda9.ready2race.backend.app.liveDashboard.entity.*
import de.lambda9.ready2race.backend.app.substitution.control.SubstitutionRepo
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo
import de.lambda9.ready2race.backend.data.Timecode
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

    fun getLiveDashboard(eventId: UUID): App<LiveDashboardError, ApiResponse.Dto<LiveDashboardDto>> =
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

            // requirement id -> assigned named participants (null element = global assignment)
            val requirementAssignments = requirementRecords.groupBy(
                { it[PARTICIPANT_REQUIREMENT.ID]!! },
                { it[EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT] },
            )
            val requirementInfos = requirementRecords.distinctBy { it[PARTICIPANT_REQUIREMENT.ID] }

            val checksByKey = checkRecords.associateBy {
                it[PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT]!! to
                    it[PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT_REQUIREMENT]!!
            }

            val paidAtsByClub = invoiceRecords.groupBy(
                { it[INVOICE_FOR_EVENT_REGISTRATION.CLUB] },
                { it[INVOICE_FOR_EVENT_REGISTRATION.PAID_AT] },
            )

            // (round, registration) -> substitutions applying to that team in that round, ordered
            val substitutionsByKey = substitutionRecords
                .groupBy { it.competitionSetupRoundId!! to it.competitionRegistrationId!! }
                .mapValues { (_, subs) -> subs.sortedBy { it.orderForRound } }

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

                val subs = substitutionsByKey[roundId to registrationId] ?: emptyList()

                // Post-substitution crew: this is the crew that actually starts, incl. taken-over roles.
                val resolved = !CompetitionExecutionService.getActuallyParticipatingParticipants(registered, subs)

                // Who was substituted in for whom, so the dashboard can show the change
                // instead of silently presenting a different crew than the one that was entered.
                val substitutedForByParticipant = subs
                    .filter { it.participantIn != null }
                    .associateBy({ it.participantIn!!.id!! }, { it })

                val participants = resolved.map { p ->
                    val substitution = substitutedForByParticipant[p.id]
                    val replaced = substitution?.participantOut

                    val requirements = requirementInfos
                        .filter { req ->
                            LiveDashboardLogic.requirementApplies(
                                requirementAssignments[req[PARTICIPANT_REQUIREMENT.ID]!!] ?: emptyList(),
                                p.namedParticipantId,
                            )
                        }
                        .map { req ->
                            val check = checksByKey[p.id to req[PARTICIPANT_REQUIREMENT.ID]!!]
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

                KIO.ok(
                    LiveDashboardTeamDto(
                        teamId = registrationId,
                        teamName = teamName,
                        clubName = clubName,
                        actualClubName = singletonOrFallback(
                            resolved.map { it.externalClubName }.toSet(),
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
                        participants = participants,
                    )
                )
            }

            fun buildMatchDto(match: Record): App<Nothing, LiveDashboardMatchDto> = KIO.comprehension {
                val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
                val startTime = match[COMPETITION_MATCH.START_TIME]
                val running = match[COMPETITION_MATCH.CURRENTLY_RUNNING] == true

                val teams = !(teamsByMatch[matchId] ?: emptyList())
                    .groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION]!! }
                    .toList()
                    .traverse { (registrationId, rows) -> buildTeamDto(registrationId, rows, startTime) }
                    .map { list -> list.sortedWith(compareBy(nullsLast()) { it.startNumber }) }

                KIO.ok(
                    LiveDashboardMatchDto(
                        matchId = matchId,
                        state = LiveDashboardLogic.deriveMatchState(running, startTime, teams.map { LiveDashboardLogic.teamHasResult(it.place, it.failed, it.deregistered) }),
                        competitionId = match.get("competition_id", UUID::class.java)!!,
                        competitionName = match.get("competition_name", String::class.java) ?: "",
                        categoryName = match[COMPETITION_VIEW.CATEGORY_NAME],
                        roundName = match.get("round_name", String::class.java),
                        matchName = match.get("match_name", String::class.java),
                        executionOrder = match[COMPETITION_SETUP_MATCH.EXECUTION_ORDER] ?: 0,
                        startTime = startTime,
                        currentlyRunning = running,
                        elapsedMinutes = if (running) startTime?.let { Duration.between(it, now).toMinutes().coerceAtLeast(0) } else null,
                        teams = teams,
                    )
                )
            }

            val matches = !matchRecords.traverse { match -> buildMatchDto(match) }

            KIO.ok(ApiResponse.Dto(LiveDashboardDto(matches)))
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
        val candidates = if (chainEnabled) {
            val finishedStart = !LiveDashboardRepo.getMatchStartTime(matchId).orDie()
            (!LiveDashboardRepo.getActivationCandidates(eventId).orDie())
                .filter { candidate ->
                    val start = candidate[COMPETITION_MATCH.START_TIME]
                    finishedStart == null || (start != null && start > finishedStart)
                }
        } else {
            emptyList()
        }

        !setRunning(matchId, false, userId)
        !activateNext(candidates, userId)

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
