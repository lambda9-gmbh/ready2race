package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.competitionExecution.entity.MatchTeamLapDto
import java.util.UUID

data class RunningMatchTeamInfo(
    val teamId: UUID,
    val teamName: String?,
    /** Die n-te Mannschaft dieses Vereins in diesem Wettkampf, aus `competition_registration`. */
    val teamNumber: Int?,
    /**
     * Die Startposition im Lauf, aus `competition_match_team.start_number`. Nicht nullbar: die
     * Spalte ist seit Migration V202507040930 NOT NULL, und die Abfrage dahinter liest sie aus
     * ihrer führenden Tabelle - Begründung bei `EventInfoService.getMatchResultTeams`.
     */
    val startNumber: Int,
    val clubName: String?,
    /**
     * Die Vereine, die die Athleten dieses Bootes tragen, als Kette in Bootsreihenfolge - kurz
     * und lang, damit die Anzeige nach Platz entscheiden kann. Fehlt jede Angabe zur Crew, bleiben
     * beide leer und der meldende [clubName] tritt an ihre Stelle (siehe eventInfo/control).
     */
    val clubsShort: String?,
    val clubsFull: String?,
    val currentScore: Int?,
    val currentPosition: Int?,
    /**
     * Teilergebnis eines noch laufenden Laufs: eine externe Zeitmessung schreibt Zeiten ein,
     * während die letzten Boote noch fahren. Null heißt "noch keine Zeit", nicht "keine Zeit".
     */
    val timeString: String?,
    /**
     * Zeitstrafe wie erfasst. Sie ist nur ausgewiesen, nie verrechnet - [timeString] enthält sie
     * bei externer Zeitmessung bereits (siehe Migration V202607301500).
     */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    val failed: Boolean,
    /** Vergebenes Freilos - kommt ohne Start weiter (V202608151900). */
    val bye: Boolean = false,
    val failedReason: String?,
    /**
     * Für diese Runde abgemeldet. Reiner Ausweis für die Anzeige - die Zustandsableitung des Laufs
     * behandelt eine Abmeldung als "erledigt" (`LiveDashboardLogic.teamIsSettled`), nie als
     * Wertung.
     */
    val deregistered: Boolean = false,
    val deregistrationReason: String? = null,
    val participants: List<UpcomingMatchParticipantInfo>,
    /** Zwischenzeiten dieses Boots, nachträglich gefüllt (EventInfoService.attachLaps). */
    val laps: List<MatchTeamLapDto> = emptyList(),
)
