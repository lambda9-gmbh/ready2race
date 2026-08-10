package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchStatusDto
import java.time.LocalDateTime
import java.util.UUID

/**
 * Ein Lauf im Tab „Live" der öffentlichen Ergebnisanzeige: aktiviert ODER anstehend, jeder mit
 * seinem Zustand.
 *
 * Bis zum 09.08.2026 führte dieser Tab ausschließlich aktivierte Läufe und zeigte keinen
 * Zustand - „In Vorbereitung" und „Läuft" sahen dort identisch aus, und ein Lauf, der gleich dran
 * ist, stand gar nicht erst da.
 *
 * [status] ist die einzige Zustandsangabe dieses DTOs. Es gibt bewusst keine Felder `activatedAt`
 * oder `finishedAt` daneben: die Anzeige soll nichts selbst ableiten können, sondern lesen, was
 * [de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic.matchStatus] entschieden
 * hat.
 */
data class LiveMatchInfo(
    val matchId: UUID,
    /** Null bei einem Programmpunkt (FREE-Platzhalter, siehe [name]) - der hängt an keinem Wettkampf. */
    val competitionId: UUID?,
    val competitionName: String,
    val categoryName: String?,
    val roundName: String?,
    val matchName: String?,
    /** Geplanter Start aus dem Zeitplan. */
    val startTime: LocalDateTime?,
    val status: MatchStatusDto,
    val executionOrder: Int,
    /**
     * Der Lauf ist abgesagt („Findet nicht statt"). Er bleibt trotzdem in der Liste stehen: ein
     * spurlos verschwundener Lauf ist für einen Zuschauer nicht von einem Anzeigefehler zu
     * unterscheiden. [teams] ist dann immer leer.
     */
    val cancelled: Boolean = false,
    /** Platzhalter für eine noch nicht erzeugte Runde; [teams] ist dann immer leer. */
    val pendingRound: Boolean = false,
    /** Name eines Programmpunkts (FREE-Slot wie „Mittagspause"), sonst null. */
    val name: String? = null,
    val teams: List<RunningMatchTeamInfo> = emptyList(),
)
