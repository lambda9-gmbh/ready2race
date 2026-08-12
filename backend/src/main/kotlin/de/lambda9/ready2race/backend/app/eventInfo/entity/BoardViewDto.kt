package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyRank
import de.lambda9.ready2race.backend.app.event.entity.EventNoticeDto
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

/** Daten eines Listen-Elements; je [mode] ist genau eine der Listen gefüllt. */
data class BoardListDto(
    val mode: BoardListMode,
    val matches: List<AthleteBoardMatch>,
    val results: List<AthleteBoardResult>,
    /** Nur im Modus SCHEDULE gefüllt: das komplette Tagesprogramm aus dem Zeitplan. */
    val program: List<BoardProgramEntry> = emptyList(),
)

enum class BoardProgramState { FINISHED, RUNNING, UPCOMING }

/**
 * Eine Zeile des Tagesprogramms: ein Zeitplan-Slot mit seinem Zustand. [name] trägt
 * Programmpunkte (Pausen); für echte Läufe stehen Wettkampf/Runde/Lauf. Pausen kommen
 * nur mit, wenn die Veranstaltung sie auf öffentlichen Anzeigen zeigt.
 */
data class BoardProgramEntry(
    val startTime: LocalDateTime?,
    val name: String? = null,
    val competitionName: String? = null,
    val competitionShortName: String? = null,
    val roundName: String? = null,
    val matchName: String? = null,
    val state: BoardProgramState,
)

/**
 * Das Podium einer Ehrung — dieselben Ränge, die der Siegerehrungsbogen druckt.
 * [ranks] leer heißt: für diese Ehrung gibt es (noch) keine geehrten Boote.
 */
data class BoardCeremonyDto(
    val competitionId: UUID,
    val ratingCategoryId: UUID?,
    val competitionIdentifier: String,
    val competitionShortName: String?,
    val competitionName: String,
    val ratingCategoryName: String?,
    val ranks: List<AwardCeremonyRank>,
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
    /** Je konfiguriertem Siegerehrungs-Element eine Ehrung; fehlende bleiben einfach aus. */
    val ceremonies: List<BoardCeremonyDto> = emptyList(),
    /** Der veranstaltungsweite Hinweisbanner (z.B. Wetterwarnung); null = kein Banner. */
    val notice: EventNoticeDto? = null,
    /**
     * Aktuelle Verspätung in Sekunden: `started_at − start_time` des zuletzt gestarteten Laufs
     * (BoardLogic.currentDelaySeconds). Negativ = Verfrühung. Null, wenn noch nichts gestartet
     * ist — oder das Board kein DELAY-Element hat (needs-Muster, siehe BoardDataNeeds.delay).
     */
    val currentDelaySeconds: Long? = null,
)
