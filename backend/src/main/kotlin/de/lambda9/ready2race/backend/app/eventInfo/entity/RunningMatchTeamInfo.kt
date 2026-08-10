package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.util.UUID

data class RunningMatchTeamInfo(
    val teamId: UUID,
    val teamName: String?,
    /** Die n-te Mannschaft dieses Vereins in diesem Wettkampf, aus `competition_registration`. */
    val teamNumber: Int?,
    val startNumber: Int?,
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
    val failedReason: String?,
    val participants: List<UpcomingMatchParticipantInfo>
)
