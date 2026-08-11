package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyKeyRequest
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

    // Kürzer als die alten 5 s der Athleten-Anzeige: der Abfragetakt darf bis 3 s
    // hinunter (Sprecherinnen-Wunsch), und ein Zwischenspeicher, der länger hält als der
    // Takt, machte den schnellen Takt wirkungslos.
    const val CACHE_TTL_SECONDS = 2

    /** Wie [AthleteBoardLogic.isCacheFresh], nur gegen die kürzere Board-TTL. */
    fun isCacheFresh(builtAt: java.time.LocalDateTime, now: java.time.LocalDateTime): Boolean =
        builtAt.plusSeconds(CACHE_TTL_SECONDS.toLong()).isAfter(now)

    data class BoardDataNeeds(
        val runningLimit: Int,
        val upcomingLimit: Int,
        val resultsLimit: Int,
        val offsets: Set<Int>,
        val listLimits: Map<BoardListMode, Int>,
        /** Sprecherinnen-Details (Crew einzeln, Jahrgänge, meldender Verein) angefordert. */
        val crewDetails: Boolean = false,
        /** Weiterkommens-Regel („N Boote → Finale") angefordert. */
        val advancement: Boolean = false,
        /** Tagesprogramm (Listenmodus SCHEDULE) angefordert. */
        val schedule: Boolean = false,
        /** Die Ehrungen aller Siegerehrungs-Elemente, dedupliziert. */
        val ceremonies: List<AwardCeremonyKeyRequest> = emptyList(),
        /**
         * Bedingungen je Person (Sprecher-Kachel MATCH_DETAIL). Bewusst ein eigenes Flag: die
         * Auflösung kostet drei zusätzliche Abfragen je Board-Aufbau und die Nutzlast wächst je
         * Person — das zahlt nur, wer die Kachel tatsächlich konfiguriert hat.
         */
        val requirements: Boolean = false,
    )

    /**
     * Welche Datenmengen ein Board braucht. Listen desselben Modus werden auf ihr
     * größtes Limit zusammengelegt; die Anzeige schneidet je Element selbst zu.
     */
    fun dataNeeds(config: BoardConfig): BoardDataNeeds {
        val elements = config.tiles.flatMap { it.elements }
        // Die Sprecher-Kachel wählt ihren Lauf über dieselbe Timeline wie MATCH — ihre Offsets
        // zählen deshalb in dieselbe Slot-Menge.
        val offsets = elements
            .filter { it.type == BoardElementType.MATCH || it.type == BoardElementType.MATCH_DETAIL }
            .mapNotNull { it.offset }
            .toSet()
        val listLimits = elements
            .filter { it.type == BoardElementType.MATCH_LIST && it.listMode != null }
            .groupBy { it.listMode!! }
            .mapValues { (_, es) -> es.maxOf { it.limit ?: BoardLimits.MIN_LIST_LIMIT } }

        val maxNegative = offsets.filter { it < 0 }.minOrNull()?.let { -it } ?: 0
        val maxPositive = offsets.filter { it > 0 }.maxOrNull() ?: 0

        val matchElements = elements.filter { it.type == BoardElementType.MATCH }
        // MATCH_DETAIL ist maximale Detailtiefe ohne Schalter: Crew-Details, Weiterkommens-Regel
        // und Bedingungen sind dort immer an.
        val matchDetail = elements.any { it.type == BoardElementType.MATCH_DETAIL }
        val crewDetails = matchDetail || matchElements.any {
            it.showCrewDetails == true || it.showBirthYears == true || it.showRegisteringClub == true
        }
        val advancement = matchDetail || matchElements.any { it.showAdvancement == true }
        val ceremonies = elements
            .filter { it.type == BoardElementType.AWARD_CEREMONY && it.competitionId != null }
            .map { AwardCeremonyKeyRequest(competitionId = it.competitionId!!, ratingCategoryId = it.ratingCategoryId) }
            .distinct()

        // Negative Offsets können parallel laufende Läufe treffen, bevor sie die
        // Ergebnisse erreichen — deshalb |min|+1 laufende Läufe abrufen, nie unter 1,
        // damit „läuft gerade nichts" von „nichts abgefragt" unterscheidbar bleibt.
        return BoardDataNeeds(
            runningLimit = maxOf(1, maxNegative + 1, listLimits[BoardListMode.RUNNING] ?: 0),
            upcomingLimit = maxOf(1, maxPositive, listLimits[BoardListMode.UPCOMING] ?: 0),
            resultsLimit = maxOf(1, maxNegative, listLimits[BoardListMode.RESULTS] ?: 0),
            offsets = offsets,
            listLimits = listLimits,
            crewDetails = crewDetails,
            advancement = advancement,
            schedule = BoardListMode.SCHEDULE in listLimits,
            ceremonies = ceremonies,
            requirements = matchDetail,
        )
    }

    /**
     * Löst einen Offset gegen die drei Blöcke auf. [running] in Arena-Reihenfolge
     * (aufsteigend nach geplantem Start, so liefert `CompetitionMatchRepo.getRunningMatches`;
     * ein Lauf in Vorbereitung steht damit hinter dem fahrenden), [upcoming] aufsteigend
     * nach geplantem Start, [results] neuestes zuerst (`getMatchResults` sortiert nach
     * `UPDATED_AT desc`) — die Reihenfolgen, auf denen [BoardService.getBoardView] aufbaut.
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
