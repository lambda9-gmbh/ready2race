package de.lambda9.ready2race.backend.app.raceclocker.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Lauf, der für den automatischen Abruf überhaupt in Frage kommt. Ob er auch beobachtet wird,
 * entscheidet erst `RaceClockerPollLogic.isWatched` anhand von [startTime] und [currentlyRunning] -
 * das Zeitfenster steht in der Logik und nicht in der Abfrage, damit es prüfbar bleibt.
 *
 * [matchId] ist wie überall `competition_match.competition_setup_match`.
 */
data class RaceClockerPollCandidate(
    val matchId: UUID,
    val competitionId: UUID,
    val startTime: LocalDateTime?,
    val currentlyRunning: Boolean,
    val target: RaceClockerMatchTarget,
)
