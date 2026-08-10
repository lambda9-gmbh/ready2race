package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.eventInfo.entity.*

/**
 * Reine Logik der Boards: Timeline-Auflösung und Datenbedarf, ohne Datenbank- und
 * Ktor-Bezug — prüfbar ohne laufende Umgebung, analog [AthleteBoardLogic].
 *
 * Die Timeline eines Tages: beendete Läufe (chronologisch) → laufende (nach
 * tatsächlichem Start) → anstehende (nach geplantem Start). Der Cursor 0 steht auf dem
 * zuletzt gestarteten, noch laufenden Lauf; läuft nichts, steht er zwischen letztem
 * Ergebnis und nächstem Start und 0 ist leer. Die Darstellung folgt dem Zustand des
 * Laufs, nicht dem Vorzeichen des Offsets.
 */
object BoardLogic {

    const val CACHE_TTL_SECONDS = AthleteBoardLogic.CACHE_TTL_SECONDS

    data class BoardDataNeeds(
        val runningLimit: Int,
        val upcomingLimit: Int,
        val resultsLimit: Int,
        val offsets: Set<Int>,
        val listLimits: Map<BoardListMode, Int>,
    )

    /**
     * Welche Datenmengen ein Board braucht. Listen desselben Modus werden auf ihr
     * größtes Limit zusammengelegt; die Anzeige schneidet je Element selbst zu.
     */
    fun dataNeeds(config: BoardConfig): BoardDataNeeds {
        val elements = config.tiles.flatMap { it.elements }
        val offsets = elements
            .filter { it.type == BoardElementType.MATCH }
            .mapNotNull { it.offset }
            .toSet()
        val listLimits = elements
            .filter { it.type == BoardElementType.MATCH_LIST && it.listMode != null }
            .groupBy { it.listMode!! }
            .mapValues { (_, es) -> es.maxOf { it.limit ?: BoardLimits.MIN_LIST_LIMIT } }

        val maxNegative = offsets.filter { it < 0 }.minOrNull()?.let { -it } ?: 0
        val maxPositive = offsets.filter { it > 0 }.maxOrNull() ?: 0

        // Negative Offsets können parallel laufende Läufe treffen, bevor sie die
        // Ergebnisse erreichen — deshalb |min|+1 laufende Läufe abrufen, nie unter 1,
        // damit „läuft gerade nichts" von „nichts abgefragt" unterscheidbar bleibt.
        return BoardDataNeeds(
            runningLimit = maxOf(1, maxNegative + 1, listLimits[BoardListMode.RUNNING] ?: 0),
            upcomingLimit = maxOf(1, maxPositive, listLimits[BoardListMode.UPCOMING] ?: 0),
            resultsLimit = maxOf(1, maxNegative, listLimits[BoardListMode.RESULTS] ?: 0),
            offsets = offsets,
            listLimits = listLimits,
        )
    }

    /**
     * Löst einen Offset gegen die drei Blöcke auf. [running] aufsteigend nach
     * tatsächlichem Start, [upcoming] aufsteigend nach geplantem Start, [results]
     * neuestes zuerst — genau die Reihenfolgen, die die bestehenden Abfragen liefern
     * (verifiziert in [BoardService.getBoardView]).
     */
    fun resolveOffset(
        offset: Int,
        running: List<AthleteBoardMatch>,
        upcoming: List<AthleteBoardMatch>,
        results: List<AthleteBoardResult>,
    ): BoardMatchSlotDto = when {
        offset > 0 -> BoardMatchSlotDto(offset, upcoming.getOrNull(offset - 1), null)
        offset == 0 -> BoardMatchSlotDto(offset, running.lastOrNull(), null)
        else -> {
            // Rückwärts vom Cursor: erst die früher gestarteten, noch laufenden Läufe,
            // dann die Ergebnisse (neuestes zuerst).
            val earlierRunning = if (running.isEmpty()) emptyList() else running.dropLast(1).reversed()
            val steps = -offset
            if (steps <= earlierRunning.size) {
                BoardMatchSlotDto(offset, earlierRunning[steps - 1], null)
            } else {
                BoardMatchSlotDto(offset, null, results.getOrNull(steps - earlierRunning.size - 1))
            }
        }
    }
}
