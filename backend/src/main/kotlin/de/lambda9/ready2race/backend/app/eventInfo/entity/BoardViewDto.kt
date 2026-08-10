package de.lambda9.ready2race.backend.app.eventInfo.entity

import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Platz auf der Tages-Timeline. Höchstens eines von [match] und [result] ist gefüllt;
 * beide leer heißt: an dieser Position ist (noch) nichts — die Kachel zeigt ihren
 * Leerzustand, statt zu verschwinden.
 */
data class BoardMatchSlotDto(
    val offset: Int,
    val match: AthleteBoardMatch?,
    val result: AthleteBoardResult?,
)

/** Daten eines Listen-Elements; je [mode] ist genau eine der beiden Listen gefüllt. */
data class BoardListDto(
    val mode: BoardListMode,
    val matches: List<AthleteBoardMatch>,
    val results: List<AthleteBoardResult>,
)

/** Alles, was die Anzeige eines Boards braucht, in einer Antwort. */
data class BoardViewDto(
    val boardId: UUID,
    val eventName: String,
    val serverTime: LocalDateTime,
    val refreshIntervalSeconds: Int,
    val config: BoardConfig,
    val slots: List<BoardMatchSlotDto>,
    val lists: List<BoardListDto>,
)
