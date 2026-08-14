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
        /**
         * Gemessene Boot-Starts (`competition_match_team.started_at`, Sprecher-Kachel
         * MATCH_DETAIL). Beim Zeitfahren starten die Boote versetzt — die Sprecherin will
         * je Boot wissen, wann es losgefahren ist. Eigenes Flag wie [requirements]: die
         * Zusatzabfrage und die Nutzlast zahlt nur ein Board mit der Kachel.
         */
        val boatStarts: Boolean = false,
        /**
         * Aktuelle Verspätung (DELAY-Element) angefordert. Eigenes Flag statt „immer mitliefern":
         * der Running-Block trägt die Zahl nicht verlässlich — der zuletzt gestartete Lauf kann
         * längst beendet sein und ist dann dort verschwunden. Die Zahl braucht deshalb ihre
         * eigene kleine Abfrage, und die zahlt nur ein Board mit DELAY-Element.
         */
        val delay: Boolean = false,
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
        // Das Stream-Overlay hängt sich an dieselbe Timeline: Offset 0 ist der zuletzt
        // gestartete laufende Lauf (genau die Regel „bei mehreren gewinnt der zuletzt
        // gestartete" aus der Spec), −1 das jüngste Ergebnis, +1 der nächste anstehende.
        val streamElements = elements.filter { it.type == BoardElementType.STREAM }
        val streamOffsets = streamElements
            .flatMap {
                when (it.streamMode ?: StreamOverlayMode.AUTO) {
                    StreamOverlayMode.AUTO -> listOf(0, -1)
                    StreamOverlayMode.RUNNING -> listOf(0)
                    StreamOverlayMode.RESULTS -> listOf(-1)
                    StreamOverlayMode.UPCOMING -> listOf(1)
                    // LAPS bleibt auf demselben Slot wie RUNNING — nur die Kachel zeigt
                    // statt der Kurzkarte die Zwischenzeiten mit Eintreffzeit.
                    StreamOverlayMode.LAPS -> listOf(0)
                    // Nur-Uhr-Quelle: braucht denselben laufenden Lauf wie LAPS.
                    StreamOverlayMode.CLOCK -> listOf(0)
                    // UPCOMING_LIST behält zusätzlich den Einzel-Slot +1 (für Kacheln, die
                    // nur ihn brauchen) — die Liste selbst kommt über listLimits (unten).
                    StreamOverlayMode.UPCOMING_LIST -> listOf(1)
                }
            }
            .toSet()
        val allOffsets = offsets + streamOffsets
        // UPCOMING_LIST ist inhaltlich ein implizites MATCH_LIST(UPCOMING, 5): es fließt in
        // dieselbe listLimits-Menge ein, damit BoardService.getBoardView den `lists`-Block
        // ohne Sonderfall befüllt (siehe BoardViewDto.lists-Aufbau).
        val streamUpcomingList = streamElements.any {
            (it.streamMode ?: StreamOverlayMode.AUTO) == StreamOverlayMode.UPCOMING_LIST
        }
        val streamCrew = streamElements.any {
            (it.streamCrew ?: StreamCrewDisplay.CLUBS_FIRST) != StreamCrewDisplay.CLUBS_ONLY
        }
        val streamAdvancement = streamElements.any { it.showAdvancement == true }
        val listLimits = run {
            val base = elements
                .filter { it.type == BoardElementType.MATCH_LIST && it.listMode != null }
                .groupBy { it.listMode!! }
                .mapValues { (_, es) -> es.maxOf { it.limit ?: BoardLimits.MIN_LIST_LIMIT } }
            if (streamUpcomingList) {
                base + (BoardListMode.UPCOMING to maxOf(base[BoardListMode.UPCOMING] ?: 0, 5))
            } else base
        }

        val maxNegative = allOffsets.filter { it < 0 }.minOrNull()?.let { -it } ?: 0
        val maxPositive = allOffsets.filter { it > 0 }.maxOrNull() ?: 0

        val matchElements = elements.filter { it.type == BoardElementType.MATCH }
        // MATCH_DETAIL ist maximale Detailtiefe ohne Schalter: Crew-Details, Weiterkommens-Regel
        // und Bedingungen sind dort immer an.
        val matchDetail = elements.any { it.type == BoardElementType.MATCH_DETAIL }
        val crewDetails = matchDetail || matchElements.any {
            it.showCrewDetails == true || it.showBirthYears == true || it.showRegisteringClub == true
        } || streamCrew
        val advancement = matchDetail || matchElements.any { it.showAdvancement == true } || streamAdvancement
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
            offsets = allOffsets,
            listLimits = listLimits,
            crewDetails = crewDetails,
            advancement = advancement,
            schedule = BoardListMode.SCHEDULE in listLimits,
            ceremonies = ceremonies,
            requirements = matchDetail,
            boatStarts = matchDetail,
            delay = elements.any { it.type == BoardElementType.DELAY },
        )
    }

    /**
     * Programmpunkte (FREE-Slots) haben keinen eigenen Erledigt-Zustand: niemand „beendet" eine
     * Mittagspause. Sie gelten als vorbei, sobald ein im Programm SPÄTERER Lauf gestartet,
     * aktiviert oder beendet ist — Programm-Reihenfolge ist die geplante Startzeit, dieselbe
     * Ordnung, in der das Tagesprogramm seine Zustände zeigt. Ohne diese Regel blieb eine
     * 15-Uhr-Besprechung bei Verspätung ewig „als Nächstes" stehen, während längst die Läufe
     * vom Nachmittag fuhren (Prod-Screenshot vom 11.08.2026).
     *
     * [latestProgressStartTime] ist die geplante Startzeit des spätesten Laufs mit Aktivität;
     * null, solange noch gar nichts passiert ist — dann ist auch nichts überholt.
     */
    fun freeSlotPassed(
        slotStartTime: java.time.LocalDateTime?,
        latestProgressStartTime: java.time.LocalDateTime?,
    ): Boolean =
        latestProgressStartTime != null && slotStartTime != null &&
            !slotStartTime.isAfter(latestProgressStartTime)

    /**
     * Dieselbe Regel im Tagesprogramm: überholte Programmpunkte werden als FINISHED markiert,
     * damit der mitlaufende Ausschnitt (programForElement) nicht ewig an ihnen hängen bleibt.
     * Die Schwelle kommt aus den eigenen Einträgen — genau der Quelle, aus der das Programm
     * auch RUNNING/FINISHED ableitet, keine zweite Wahrheit.
     */
    fun markPassedFreeSlots(program: List<BoardProgramEntry>): List<BoardProgramEntry> {
        val latestProgress = program
            .filter { it.state != BoardProgramState.UPCOMING }
            .mapNotNull { it.startTime }
            .maxOrNull()
        return program.map { entry ->
            if (
                entry.name != null &&
                entry.state == BoardProgramState.UPCOMING &&
                freeSlotPassed(entry.startTime, latestProgress)
            ) entry.copy(state = BoardProgramState.FINISHED)
            else entry
        }
    }

    /**
     * Aktuelle Verspätung der Veranstaltung: `started_at − start_time` des zuletzt (nach
     * Ist-Start) gestarteten Laufs. Negativ = Verfrühung. Null, wenn noch nichts gestartet ist
     * oder der zuletzt gestartete Lauf keine geplante Zeit hat — dann gibt es nichts zu
     * vergleichen. [starts] sind Paare (Ist-Start, geplanter Start).
     */
    fun currentDelaySeconds(
        starts: List<Pair<java.time.LocalDateTime, java.time.LocalDateTime?>>,
    ): Long? {
        val (startedAt, planned) = starts.maxByOrNull { it.first } ?: return null
        if (planned == null) return null
        return java.time.Duration.between(planned, startedAt).seconds
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
