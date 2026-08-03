package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.eventSchedule.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID

object EventScheduleService {

    fun getSchedule(eventId: UUID): App<EventScheduleError, ApiResponse.Dto<EventScheduleDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                return@comprehension KIO.fail(EventScheduleError.EventNotFound(eventId))
            }

            val slotRecords = !EventScheduleRepo.getSlots(eventId).orDie()
            val unplanned = !EventScheduleRepo.getUnplannedSetupMatches(eventId).orDie()

            val slots = slotRecords.map { r ->
                val isFree = r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
                val matchExists = r.get("match_exists", Boolean::class.java) == true
                EventScheduleSlotDto(
                    id = r[EVENT_SCHEDULE_SLOT.ID]!!,
                    startTime = r[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                    state = EventScheduleLogic.deriveSlotState(
                        isFree = isFree,
                        skipped = r[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null,
                        roundMaterialized = r.get("round_materialized", Boolean::class.java) == true,
                        matchExists = matchExists,
                    ),
                    name = r[EVENT_SCHEDULE_SLOT.NAME],
                    durationMinutes = r[EVENT_SCHEDULE_SLOT.DURATION_MINUTES],
                    competitionId = r.get("competition_id", UUID::class.java),
                    competitionName = r.get("competition_name", String::class.java),
                    roundName = r.get("round_name", String::class.java),
                    matchName = r.get("match_name", String::class.java),
                    matchId = if (matchExists) r[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] else null,
                    matchStartedAt = r.get("match_started_at", java.time.LocalDateTime::class.java),
                    matchFinishedAt = r.get("match_finished_at", java.time.LocalDateTime::class.java),
                )
            }

            val unplannedDtos = unplanned.map { r ->
                UnplannedSetupMatchDto(
                    setupMatchId = r[COMPETITION_SETUP_MATCH.ID]!!,
                    competitionId = r.get("competition_id", UUID::class.java)!!,
                    competitionName = r.get("competition_name", String::class.java) ?: "",
                    roundName = r.get("round_name", String::class.java) ?: "",
                    matchName = r.get("match_name", String::class.java),
                )
            }

            KIO.ok(ApiResponse.Dto(EventScheduleDto(slots, unplannedDtos)))
        }
}
