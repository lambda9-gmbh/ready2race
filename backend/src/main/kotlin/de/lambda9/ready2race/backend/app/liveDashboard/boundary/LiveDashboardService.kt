package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.liveDashboard.control.LiveDashboardRepo
import de.lambda9.ready2race.backend.app.liveDashboard.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.singletonOrFallback
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
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

            val teamsByMatch = teamRecords.groupBy { it.get("match_id", UUID::class.java)!! }
            val now = LocalDateTime.now()

            val matches = matchRecords.map { match ->
                val matchId = match[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!
                val startTime = match[COMPETITION_MATCH.START_TIME]
                val running = match[COMPETITION_MATCH.CURRENTLY_RUNNING] == true

                val teams = (teamsByMatch[matchId] ?: emptyList())
                    .groupBy { it[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION]!! }
                    .map { (registrationId, rows) ->
                        val first = rows.first()
                        val clubId = first.get("club_id", UUID::class.java)

                        val participants = rows.mapNotNull { row ->
                            row.get("participant_id", UUID::class.java)?.let { participantId ->
                                val namedParticipantId = row.get("named_participant_id", UUID::class.java)
                                val requirements = requirementInfos
                                    .filter { req ->
                                        LiveDashboardLogic.requirementApplies(
                                            requirementAssignments[req[PARTICIPANT_REQUIREMENT.ID]!!] ?: emptyList(),
                                            namedParticipantId,
                                        )
                                    }
                                    .map { req ->
                                        val check = checksByKey[participantId to req[PARTICIPANT_REQUIREMENT.ID]!!]
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
                                    participantId = participantId,
                                    firstName = row[PARTICIPANT.FIRSTNAME] ?: "",
                                    lastName = row[PARTICIPANT.LASTNAME] ?: "",
                                    namedRole = row.get("named_role", String::class.java),
                                    year = row[PARTICIPANT.YEAR],
                                    gender = row[PARTICIPANT.GENDER]?.name,
                                    externalClubName = row[PARTICIPANT.EXTERNAL_CLUB_NAME],
                                    requirements = requirements,
                                )
                            }
                        }

                        LiveDashboardTeamDto(
                            teamId = registrationId,
                            teamName = first.get("team_name", String::class.java),
                            clubName = first.get("club_name", String::class.java),
                            actualClubName = singletonOrFallback(
                                rows.map { it[PARTICIPANT.EXTERNAL_CLUB_NAME] }.toSet(),
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
                            deregistered = first.get("deregistered", Boolean::class.java) == true,
                            deregisteredReason = first.get("deregistration_reason", String::class.java),
                            invoiceState = LiveDashboardLogic.deriveInvoiceState(
                                clubId?.let { paidAtsByClub[it] } ?: emptyList()
                            ),
                            participants = participants,
                        )
                    }
                    .sortedWith(compareBy(nullsLast()) { it.startNumber })

                LiveDashboardMatchDto(
                    matchId = matchId,
                    state = LiveDashboardLogic.deriveMatchState(running, startTime, teams.map { it.place != null || it.failed }),
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
            }

            KIO.ok(ApiResponse.Dto(LiveDashboardDto(matches)))
        }
}
