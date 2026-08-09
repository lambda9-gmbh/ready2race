package de.lambda9.ready2race.backend.app.eventSchedule.entity

import de.lambda9.ready2race.backend.app.event.entity.ChainProgressionMode
import java.time.LocalDateTime
import java.util.UUID

/**
 * Ob der Lauf eines Slots abgesagt ist, steht bewusst NICHT als eigenes Feld hier: [state] ist
 * dann bereits [EventScheduleSlotState.SKIPPED], und zwei Quellen für dieselbe Aussage laufen
 * früher oder später auseinander.
 */
data class EventScheduleSlotDto(
    val id: UUID,
    val startTime: LocalDateTime,
    val state: EventScheduleSlotState,
    val name: String?,
    val durationMinutes: Int?,
    val competitionId: UUID?,
    val competitionName: String?,
    /** Das Kürzel des Wettkampfs (competition_properties.identifier) - im Zeitplan-Tab dem
     * Slot-Namen vorangestellt. Null für freie Slots ohne Wettkampf. */
    val competitionIdentifier: String?,
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
    /**
     * Mannschaften des verknüpften Laufs, die im Rennen sind (ohne die aus der Vorrunde
     * mitgeführten OUT-Zeilen) - 0 ohne verknüpften Lauf.
     */
    val matchTeamsTotal: Int,
    /**
     * Davon bereits gewertet: Platz gesetzt ODER ausgeschieden ODER abgemeldet, dieselbe Regel wie
     * `LiveDashboardLogic.teamHasResult`. Aus beiden zusammen liest der Zeitplan "Teilweise
     * gewertet n/m" ab; ein eigener Zustand ist das ausdrücklich nicht.
     */
    val matchTeamsScored: Int,
)

data class UnplannedSetupMatchDto(
    val setupMatchId: UUID,
    val competitionId: UUID,
    val competitionName: String,
    val competitionIdentifier: String?,
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
