package de.lambda9.ready2race.backend.app.eventSchedule.entity

import de.lambda9.ready2race.backend.app.event.entity.ChainProgressionMode
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
    val setupMatchId: UUID?,
    /** Die Setup-Runde dieses Slots - null für FREE-Slots. Ziel für "ganze Runde überspringen". */
    val setupRoundId: UUID?,
    val matchStartedAt: LocalDateTime?,
    val matchFinishedAt: LocalDateTime?,
    /** Ob der verknüpfte Lauf gerade aktiv ist - steuert im Zeitplan-Tab, ob "Lauf aktivieren" oder
     * "Lauf beenden" angeboten wird (C1). Immer false ohne verknüpften Lauf. */
    val matchCurrentlyRunning: Boolean,
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
    /** Steuert im Zeitplan-Tab, ob "Lauf aktivieren"/"Lauf beenden" ohne Warnung durchgehen
     * (REGATTABUERO) oder als Eingriff ins Schiedsrichter-Dashboard markiert werden. */
    val chainProgressionMode: ChainProgressionMode,
)
