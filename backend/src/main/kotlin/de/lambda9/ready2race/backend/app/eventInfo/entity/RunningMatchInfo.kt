package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class RunningMatchInfo(
    val matchId: UUID,
    val matchNumber: Int?,
    val competitionId: UUID,
    val competitionName: String,
    /** Wettkampf-Kürzel (short_name) für kompakte Anzeigen; null, wenn keins gepflegt ist. */
    val competitionShortName: String? = null,
    val categoryName: String?,
    val startTime: LocalDateTime?,
    /**
     * Wann der Lauf an den Start gerufen wurde (`competition_match.activated_at`). In diesem Block
     * nie null - die Abfrage holt genau die aktivierten Läufe.
     */
    val activatedAt: LocalDateTime?,
    /**
     * Tatsächlicher Start aus `competition_match.started_at`. Null heißt: der Lauf ist an den Start
     * gerufen, aber noch nicht unterwegs (Vorbereitung am Steg) - das Aktivieren stempelt nicht,
     * das tun nur `markMatchStarted` und die Zeitnahme.
     */
    val startedAt: LocalDateTime?,
    val elapsedMinutes: Long?,
    val placeName: String?,
    val roundNumber: Int?,
    val roundName: String?,
    val matchName: String?,
    val executionOrder: Int,
    val teams: List<RunningMatchTeamInfo>
)