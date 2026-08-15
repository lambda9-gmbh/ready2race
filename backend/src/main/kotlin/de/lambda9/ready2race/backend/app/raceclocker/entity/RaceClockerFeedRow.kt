package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.validation.timecodePattern
import java.time.LocalTime
import java.util.UUID

/**
 * One participant row of a RaceClocker results feed, reduced to the fields this integration needs.
 *
 * Deliberately not modelled: `Result in seconds` (redundant with [result], and no help in telling a
 * progress state from an elimination - see [noResultReason]) and the finish timestamp
 * (its key is localised - `Finish` vs `Ziel` - and, unlike the start, nothing here needs it).
 *
 * [penaltySeconds] is carried over for display only: RaceClocker has already added it to [result],
 * so it must never be applied to the time again. Referees still need to see that a time contains a
 * penalty and why.
 */
data class RaceClockerFeedRow(
    val name: String,
    /**
     * RaceClocker's own list position, handed out per participant across the whole race. It is *not*
     * a finishing rank: it stays with the position when an entry is moved, while everything attached
     * to the entry (bib, extra info, times) travels along. That makes it the only field that reflects
     * a lane swap - see [lanesByRow].
     */
    val rank: Int?,
    val bib: Int?,
    /**
     * `null` when the feed carries no wave column.
     *
     * ABSICHTLICH TOLERANT: RaceClocker hat die Unterscheidung Zeitfahren/Läufe zwar abgeschafft
     * (11.08.2026), aber der Parser bleibt gegenüber BEIDEN Feed-Formen nachsichtig - mit und ohne
     * Wellen-Spalte. Das ist die bewusste Rückfalltür: Sollte ein Feed (Altbestand, Export einer
     * alten Startliste, Rolle rückwärts bei RaceClocker) wieder ohne Welle ankommen, darf das den
     * Abruf nicht reißen; die Zuordnung fällt dann schlicht auf die Kennungen in [ids] zurück.
     */
    val wave: String?,
    /**
     * Every UUID found in RaceClocker's "Extra info" - a match team id, a registration id, or both,
     * depending on what the start list config exported and what was mapped on import. Empty if none
     * was mapped.
     */
    val ids: List<UUID>,
    /**
     * Either a formatted time (`HH:MM:SS.d`), one of the elimination codes `DNS`, `DNF`, `DQ`, or one
     * of RaceClocker's progress states (`Not started`, `In race...`) - see [noResultReason].
     */
    val result: String?,
    /**
     * The real start time RaceClocker's clock recorded, read tolerantly from either the English or
     * the localised key (`Start`/`Startzeit`). `null` if the key is missing or its value does not
     * parse - this is auxiliary data, so a feed oddity here must never fail the pull.
     */
    val start: LocalTime?,
    /** Time penalty in seconds, already included in [result]. */
    val penaltySeconds: Int?,
    val penaltyNote: String?,
    /**
     * The intermediate marks (lap/split columns) of this row, in feed column order. RaceClocker
     * lets the timekeeper name these columns freely ("Runde 1", "Split 3", ...) and writes each as
     * a time-of-day stamp of the moment the crew passed the mark; the placeholder `00:00:00.0`
     * (= not taken) is already filtered out by the parser. Empty for races without split columns.
     */
    val laps: List<RaceClockerLapMark> = emptyList(),
) {
    val isTime: Boolean get() = result != null && timecodePattern.matches(result.trim())

    val time: String? get() = result?.trim()?.takeIf { isTime }

    /**
     * The elimination this row carries, normalised to upper case - `null` for a time and for every
     * state that only says "no result yet".
     *
     * RaceClocker has no status field: the result column carries the outcome *and* the state an entry
     * is currently in. Two of its values are pure progress states - `Not started` is what every entry
     * holds right after the start list import, `In race...` while the crew is on the water. A real
     * elimination is set separately by the timekeeper, through a dashboard dropdown that offers
     * exactly [ELIMINATION_CODES]. (The earlier assumption that anything unparsable is an elimination
     * was wrong and turned boats still on the water into failed ones.)
     *
     * Hence the allowlist: only those three codes count, everything else means "not finished yet".
     * That choice is deliberate, because the two possible mistakes are not equally expensive. Reading
     * an unknown text as an elimination claims something false - a boat that is still racing shows up
     * as failed, and a referee has to notice and undo it. Reading it as pending claims nothing: the
     * row stays untouched, the pull can be repeated as the heat progresses, and in the worst case a
     * genuine elimination is entered by hand. Not claiming anything is the smaller mistake.
     *
     * The allowlist also removes any need to keep the progress texts themselves in sync: their exact
     * wording, casing or ellipsis form (`...` vs `…`) cannot change the outcome, since they simply
     * fail to be one of the three codes.
     *
     * `Result in seconds` was checked as a second signal and dropped: it is `0` for every row without
     * a time, which only separates timed from untimed rows - something [isTime] already does - and
     * says nothing about progress state versus elimination, the one distinction that matters here.
     */
    val noResultReason: String?
        get() = result
            ?.takeUnless { isTime }
            ?.trim()
            ?.uppercase()
            ?.takeIf { it in ELIMINATION_CODES }

    /**
     * Whether this row says anything that may be written back to a match team. `false` for rows that
     * are merely waiting for their time; callers skip those instead of turning them into a result.
     */
    val hasResult: Boolean get() = isTime || noResultReason != null

    /**
     * Sagt diese Zeile ausdrücklich, dass die Mannschaft NICHT gestartet ist? Bewusst getrennt von
     * [hasResult]: Zurückgeschrieben wird ein DNS wie jeder Ausscheidungsgrund, als Startbeleg
     * zählt es nicht (`RaceClockerPollLogic.startDetected`).
     */
    val saysDidNotStart: Boolean get() = noResultReason == DID_NOT_START

    companion object {

        /**
         * The only texts in the result column that really mean a crew is out - the full contents of
         * RaceClocker's status dropdown. Matched case-insensitively after trimming; extend this set
         * (and only this set) if RaceClocker ever adds another status.
         */
        val ELIMINATION_CODES = setOf("DNS", "DNF", "DQ")

        /**
         * "Did not start" - der eine Ausscheidungsgrund, der das Gegenteil eines Starts behauptet,
         * und der einzige, der regelmäßig VOR dem Rennen feststeht (eine Abmeldung). Für das
         * Zurückschreiben an die Mannschaft zählt er wie jeder andere Grund; als Beleg dafür, dass
         * ein Lauf losgegangen ist, taugt er nicht - siehe `RaceClockerPollLogic.startDetected`.
         */
        const val DID_NOT_START = "DNS"

        /**
         * The earliest recorded start across a set of rows - what a match's `started_at` takes over
         * once RaceClocker has timed at least one of its crews. Pulled out as a pure function so the
         * selection logic (as opposed to the DB write) can be tested without any database.
         */
        fun earliestStart(rows: List<RaceClockerFeedRow>): LocalTime? = rows.mapNotNull { it.start }.minOrNull()

        /**
         * Lane numbers for one match, derived from the list positions of its rows: sorted by [rank]
         * and numbered 1..n. [rank] counts across the entire race, so it cannot be used as a lane
         * directly - only its order within the match matters.
         *
         * Rows without a rank keep the order they arrived in and are numbered last, so an incomplete
         * feed degrades to the previous behaviour instead of shuffling lanes.
         */
        fun <T> lanesByRow(rows: Map<T, RaceClockerFeedRow>): Map<T, Int> =
            rows.entries
                .sortedWith(compareBy(nullsLast()) { it.value.rank })
                .mapIndexed { index, entry -> entry.key to index + 1 }
                .toMap()
    }
}
