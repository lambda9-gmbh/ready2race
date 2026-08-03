package de.lambda9.ready2race.backend.app.eventSchedule.entity

import java.time.LocalDateTime
import java.util.UUID

data class EventScheduleSlotDto(
    val id: UUID,
    val startTime: LocalDateTime,
    val state: EventScheduleSlotState,
    val name: String?,
    val durationMinutes: Int?,
    val competitionId: UUID?,
    val competitionName: String?,
    val roundName: String?,
    val matchName: String?,
    val matchId: UUID?,
    val matchStartedAt: LocalDateTime?,
    val matchFinishedAt: LocalDateTime?,
)

data class UnplannedSetupMatchDto(
    val setupMatchId: UUID,
    val competitionId: UUID,
    val competitionName: String,
    val roundName: String,
    val matchName: String?,
)

data class EventScheduleDto(
    val slots: List<EventScheduleSlotDto>,
    val unplannedSetupMatches: List<UnplannedSetupMatchDto>,
)
