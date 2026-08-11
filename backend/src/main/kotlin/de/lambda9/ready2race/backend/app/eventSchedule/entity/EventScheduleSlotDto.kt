package de.lambda9.ready2race.backend.app.eventSchedule.entity

import de.lambda9.ready2race.backend.app.event.entity.ChainProgressionMode
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto
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
    /** Die Rennnummer des Wettkampfs (competition_properties.identifier, z. B. "17-NC") - steht im
     * Zeitplan-Tab zusammen mit dem Kurznamen vor dem Slot-Namen. Null für freie Slots. */
    val competitionIdentifier: String?,
    /** Der Kurzname des Wettkampfs (competition_properties.short_name, z. B. "CM 4x+") - im
     * Zeitplan-Tab dem Slot-Namen vorangestellt. Null für freie Slots ohne Wettkampf und für
     * Wettkämpfe, bei denen der Kurzname nicht gepflegt ist (das Feld ist optional). */
    val competitionShortName: String?,
    val roundName: String?,
    val matchName: String?,
    val matchId: UUID?,
    val setupMatchId: UUID?,
    /** Die Setup-Runde dieses Slots - null für FREE-Slots. Ziel für "ganze Runde überspringen". */
    val setupRoundId: UUID?,
    val matchStartedAt: LocalDateTime?,
    val matchFinishedAt: LocalDateTime?,
    /**
     * Wann der verknüpfte Lauf an den Start gerufen wurde - steuert im Zeitplan-Tab, ob "Lauf
     * aktivieren" oder "Lauf beenden" angeboten wird (C1), und zusammen mit [matchStartedAt], ob
     * der Slot "In Vorbereitung" oder "Läuft" zeigt. Immer null ohne verknüpften Lauf.
     */
    val matchActivatedAt: LocalDateTime?,
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
    /**
     * Gesetzt, wenn der verknüpfte Lauf ein Freilos ist - siehe `MatchStatusLogic.deriveBye`. Null
     * für freie Slots und für Slots ohne erzeugten Lauf: dort gibt es keine Mannschaften, aus denen
     * sich etwas ableiten ließe.
     */
    val bye: MatchByeDto?,
)

data class UnplannedSetupMatchDto(
    val setupMatchId: UUID,
    val competitionId: UUID,
    val competitionName: String,
    val competitionIdentifier: String?,
    val competitionShortName: String?,
    val roundName: String,
    val matchName: String?,
    /**
     * Lauf-Zustand, sofern die Runde schon gesetzt ist - alle null, solange es den Lauf noch
     * nicht gibt. Die nicht verplanten Läufe sind vor allem Dauer-Freilose; ihr Status
     * (offen/quittiert) gehört in den Zeitplan sichtbar gemacht.
     */
    val matchActivatedAt: java.time.LocalDateTime? = null,
    val matchStartedAt: java.time.LocalDateTime? = null,
    val matchFinishedAt: java.time.LocalDateTime? = null,
    val bye: de.lambda9.ready2race.backend.app.matchStatus.entity.MatchByeDto? = null,
)

data class EventScheduleDto(
    val slots: List<EventScheduleSlotDto>,
    val unplannedSetupMatches: List<UnplannedSetupMatchDto>,
    /** Steuert im Zeitplan-Tab, ob "Lauf aktivieren"/"Lauf beenden" ohne Warnung durchgehen
     * (REGATTABUERO) oder als Eingriff ins Schiedsrichter-Dashboard markiert werden. */
    val chainProgressionMode: ChainProgressionMode,
)
