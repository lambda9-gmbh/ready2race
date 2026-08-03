package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventSchedule.control.EventScheduleRepo
import de.lambda9.ready2race.backend.app.eventSchedule.entity.*
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.EventScheduleSlotRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.time.Duration
import java.time.LocalDateTime
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

    fun createSlot(
        eventId: UUID,
        request: UpsertScheduleSlotRequest,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.Created> = KIO.comprehension {
        val eventExists = !EventRepo.exists(eventId).orDie()
        if (!eventExists) {
            return@comprehension KIO.fail(EventScheduleError.EventNotFound(eventId))
        }

        val setupMatchId = request.competitionSetupMatch
        if (setupMatchId != null) {
            val belongsToEvent = !EventScheduleRepo.setupMatchExistsForEvent(eventId, setupMatchId).orDie()
            if (!belongsToEvent) {
                return@comprehension KIO.fail(EventScheduleError.SetupMatchNotFound(setupMatchId))
            }

            val alreadyPlanned = !EventScheduleRepo.slotExistsForSetupMatch(setupMatchId).orDie()
            if (alreadyPlanned) {
                return@comprehension KIO.fail(EventScheduleError.SetupMatchAlreadyPlanned(setupMatchId))
            }
        }

        val now = LocalDateTime.now()
        val record = EventScheduleSlotRecord(
            id = UUID.randomUUID(),
            event = eventId,
            startTime = request.startTime,
            competitionSetupMatch = setupMatchId,
            name = request.name,
            durationMinutes = request.durationMinutes,
            createdAt = now,
            createdBy = userId,
            updatedAt = now,
            updatedBy = userId,
        )
        val id = !EventScheduleRepo.createSlot(record).orDie()

        if (setupMatchId != null) {
            !EventScheduleRepo.stampMatchStartTime(setupMatchId, request.startTime, userId).orDie()
        }

        KIO.ok(ApiResponse.Created(id))
    }

    fun updateSlot(
        eventId: UUID,
        slotId: UUID,
        request: UpsertScheduleSlotRequest,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        val setupMatchId = request.competitionSetupMatch
        if (setupMatchId != null) {
            val belongsToEvent = !EventScheduleRepo.setupMatchExistsForEvent(eventId, setupMatchId).orDie()
            if (!belongsToEvent) {
                return@comprehension KIO.fail(EventScheduleError.SetupMatchNotFound(setupMatchId))
            }

            val alreadyPlanned =
                !EventScheduleRepo.slotExistsForSetupMatch(setupMatchId, excludeSlotId = slotId).orDie()
            if (alreadyPlanned) {
                return@comprehension KIO.fail(EventScheduleError.SetupMatchAlreadyPlanned(setupMatchId))
            }
        }

        !EventScheduleRepo.updateSlot(eventId, slotId) {
            startTime = request.startTime
            competitionSetupMatch = request.competitionSetupMatch
            name = request.name
            durationMinutes = request.durationMinutes
            updatedAt = LocalDateTime.now()
            updatedBy = userId
        }.orDie().onNullFail { EventScheduleError.SlotNotFound(slotId) }

        if (setupMatchId != null) {
            !EventScheduleRepo.stampMatchStartTime(setupMatchId, request.startTime, userId).orDie()
        }

        noData
    }

    fun deleteSlot(
        eventId: UUID,
        slotId: UUID,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        // Der Lauf behält seine letzte start_time (Spec §8) - keine Rückabwicklung auf competition_match.
        val deleted = !EventScheduleRepo.deleteSlot(eventId, slotId).orDie()

        if (deleted < 1) {
            KIO.fail(EventScheduleError.SlotNotFound(slotId))
        } else {
            noData
        }
    }

    /**
     * Skip/Unskip mit Audit (Spec §8). skip: erlaubt für FREE, WAITING und LINKED ohne
     * `started_at` am Lauf; OBSOLETE ist endgültig (SlotNotSkippable), ein bereits gestarteter
     * Lauf schlägt mit MatchAlreadyStarted fehl. unskip: erlaubt solange kein Lauf des Slots
     * `started_at` trägt.
     */
    fun setSlotSkipped(
        eventId: UUID,
        slotId: UUID,
        skipped: Boolean,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.NoData> = KIO.comprehension {
        val row = !EventScheduleRepo.getSlotWithContext(eventId, slotId).orDie()
            .onNullFail { EventScheduleError.SlotNotFound(slotId) }

        val isFree = row[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH] == null
        val matchExists = row.get("match_exists", Boolean::class.java) == true
        val matchStartedAt = row.get("match_started_at", LocalDateTime::class.java)
        val roundMaterialized = row.get("round_materialized", Boolean::class.java) == true
        val alreadySkipped = row[EVENT_SCHEDULE_SLOT.SKIPPED_AT] != null

        if (skipped) {
            val state = EventScheduleLogic.deriveSlotState(
                isFree = isFree,
                skipped = alreadySkipped,
                roundMaterialized = roundMaterialized,
                matchExists = matchExists,
            )

            if (state == EventScheduleSlotState.OBSOLETE) {
                return@comprehension KIO.fail(EventScheduleError.SlotNotSkippable(slotId))
            }
            if (matchStartedAt != null) {
                return@comprehension KIO.fail(EventScheduleError.MatchAlreadyStarted(slotId))
            }

            !EventScheduleRepo.updateSlot(eventId, slotId) {
                skippedAt = LocalDateTime.now()
                skippedBy = userId
            }.orDie().onNullFail { EventScheduleError.SlotNotFound(slotId) }
        } else {
            if (matchStartedAt != null) {
                return@comprehension KIO.fail(EventScheduleError.MatchAlreadyStarted(slotId))
            }

            !EventScheduleRepo.updateSlot(eventId, slotId) {
                skippedAt = null
                skippedBy = null
            }.orDie().onNullFail { EventScheduleError.SlotNotFound(slotId) }
        }

        noData
    }

    /**
     * Verschiebt den Zeitstrahl ab [ShiftScheduleRequest.fromSlotId] (Task 11). Betroffen sind nur
     * Slots desselben Renntags ab diesem Slot (aufsteigend) — [EventScheduleRepo.getSlots] liefert
     * bereits nach start_time sortiert, ein Tageswechsel kommt danach also nie zurück.
     * Slot-bezogene Validierung (Ziel muss in dieser Tages-Teilliste hinter dem Start-Slot liegen,
     * delta <= 0 bei COMPRESS_TO_TARGET) passiert hier bewusst VOR dem Aufruf von [EventScheduleLogic.computeShift],
     * dessen `require(targetIndex > 0)` sonst eine IllegalArgumentException werfen würde statt eines
     * fachlichen Fehlers.
     */
    fun shiftSchedule(
        eventId: UUID,
        request: ShiftScheduleRequest,
        userId: UUID,
    ): App<EventScheduleError, ApiResponse.Dto<ShiftPreviewDto>> = KIO.comprehension {
        val allSlots = !EventScheduleRepo.getSlots(eventId).orDie()

        val fromIndex = allSlots.indexOfFirst { it[EVENT_SCHEDULE_SLOT.ID] == request.fromSlotId }
        if (fromIndex == -1) {
            return@comprehension KIO.fail(EventScheduleError.SlotNotFound(request.fromSlotId))
        }

        val fromStartTime = allSlots[fromIndex][EVENT_SCHEDULE_SLOT.START_TIME]!!
        val day = fromStartTime.toLocalDate()

        val daySlotRecords = allSlots.drop(fromIndex)
            .takeWhile { it[EVENT_SCHEDULE_SLOT.START_TIME]!!.toLocalDate() == day }

        val daySlots = daySlotRecords.map {
            ShiftSlot(
                id = it[EVENT_SCHEDULE_SLOT.ID]!!,
                startTime = it[EVENT_SCHEDULE_SLOT.START_TIME]!!,
                durationMinutes = it[EVENT_SCHEDULE_SLOT.DURATION_MINUTES],
            )
        }
        val setupMatchIdBySlot = daySlotRecords.associate {
            it[EVENT_SCHEDULE_SLOT.ID]!! to it[EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH]
        }

        val deltaMinutes = when (request.mode) {
            ShiftMode.PLUS_MINUTES -> request.minutes!!
            ShiftMode.SET_TIME -> Duration.between(fromStartTime, request.newTime!!).toMinutes()
            ShiftMode.COMPRESS_TO_TARGET ->
                request.minutes ?: Duration.between(fromStartTime, request.newTime!!).toMinutes()
        }

        if (deltaMinutes == 0L) {
            return@comprehension KIO.fail(EventScheduleError.InvalidShiftRequest)
        }

        val targetSlotId = if (request.mode == ShiftMode.COMPRESS_TO_TARGET) {
            val targetIndex = daySlots.indexOfFirst { it.id == request.targetSlotId }
            if (targetIndex <= 0 || deltaMinutes < 0) {
                return@comprehension KIO.fail(EventScheduleError.InvalidShiftRequest)
            }
            request.targetSlotId
        } else {
            null
        }

        val entries = when (val result = EventScheduleLogic.computeShift(daySlots, deltaMinutes, targetSlotId)) {
            is ShiftResult.CompressionImpossible ->
                return@comprehension KIO.fail(EventScheduleError.CompressionImpossible(result.maxReductionMinutes))

            is ShiftResult.Ok -> result.entries
        }

        if (!request.dryRun) {
            val changed = entries.filter { it.oldStartTime != it.newStartTime }
            !changed.traverse { entry ->
                KIO.comprehension {
                    !EventScheduleRepo.updateSlot(eventId, entry.slotId) {
                        startTime = entry.newStartTime
                        updatedAt = LocalDateTime.now()
                        updatedBy = userId
                    }.orDie().onNullFail { EventScheduleError.SlotNotFound(entry.slotId) }

                    val setupMatchId = setupMatchIdBySlot[entry.slotId]
                    if (setupMatchId != null) {
                        !EventScheduleRepo.stampMatchStartTime(setupMatchId, entry.newStartTime, userId).orDie()
                    }

                    KIO.unit
                }
            }
        }

        KIO.ok(
            ApiResponse.Dto(
                ShiftPreviewDto(
                    entries = entries.map { ShiftPreviewEntryDto(it.slotId, it.oldStartTime, it.newStartTime) },
                    applied = !request.dryRun,
                )
            )
        )
    }
}
