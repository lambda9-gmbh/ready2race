package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDateTime
import java.util.UUID

data class UpcomingCompetitionMatchInfo(
    val matchId: UUID,
    val matchNumber: Int?,
    /** Bei einem FREE-Platzhalter (Programmpunkt, siehe [name]) gibt es keine Kompetition. */
    val competitionId: UUID?,
    val competitionName: String,
    val categoryName: String?,
    val scheduledStartTime: LocalDateTime?,
    val placeName: String?,
    val roundNumber: Int?,
    val roundName: String?,
    val matchName: String?,
    val executionOrder: Int,
    val teams: List<UpcomingMatchTeamInfo>,
    /**
     * true für einen Platzhalter aus einem wartenden Zeitstrahl-Slot (Runde noch nicht
     * erzeugt) - matchId zeigt dann auf die Setup-Zeile, nicht auf einen echten Lauf, und
     * teams ist immer leer.
     */
    val pendingRound: Boolean = false,
    /**
     * Name eines FREE-Platzhalters (Programmpunkt wie "Mittagspause") - null für echte Läufe und
     * für wartende Rund-Platzhalter ([pendingRound]). Nur gesetzt, wenn die Veranstaltung Pausen
     * auf öffentlichen Anzeigen zeigt (siehe Event.showBreaksOnPublicBoards); matchId zeigt dann
     * auf den Zeitstrahl-Slot selbst, nicht auf einen Lauf oder eine Setup-Zeile.
     */
    val name: String? = null,
    /**
     * Der Zeitstrahl-Slot dieses Laufs ist abgesagt. Der Lauf bleibt trotzdem in der Liste -
     * ein spurlos verschwundener Lauf wäre am Steg nicht von einem Anzeigefehler zu
     * unterscheiden. [teams] ist dann immer leer.
     */
    val cancelled: Boolean = false,
)