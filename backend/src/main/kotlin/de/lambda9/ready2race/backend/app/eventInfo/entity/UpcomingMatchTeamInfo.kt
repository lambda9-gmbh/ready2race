package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

data class UpcomingMatchTeamInfo(
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
    /**
     * Für diese Runde abgemeldet. Der anstehende Lauf zeigt das Boot weiter in seiner Aufstellung -
     * eine Crew am Steg kann ein spurlos verschwundenes Boot nicht von einem Anzeigefehler
     * unterscheiden -, weist es aber als abgemeldet aus.
     */
    val deregistered: Boolean = false,
    val deregistrationReason: String? = null,
    /**
     * Vergebenes Freilos (V202608151900): Das Boot steht in der Aufstellung, fährt aber nicht -
     * dieselbe Begründung wie bei [deregistered], deshalb auch hier im anstehenden Lauf.
     */
    val bye: Boolean = false,
    val participants: List<UpcomingMatchParticipantInfo>
)