package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.validation.timecodePattern
import java.time.LocalTime
import java.util.UUID

/**
 * One participant row of a RaceClocker results feed, reduced to the fields this integration needs.
 *
 * Deliberately not modelled: `Result in seconds` (redundant with [result]) and the finish timestamp
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
    /** `null` for time trial races, which have no waves. */
    val wave: String?,
    /**
     * Every UUID found in RaceClocker's "Extra info" - a match team id, a registration id, or both,
     * depending on what the start list config exported and what was mapped on import. Empty if none
     * was mapped.
     */
    val ids: List<UUID>,
    /** Either a formatted time (`HH:MM:SS.d`) or a status text such as `DNS`, `DNF` or `DQ`. */
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
) {
    /**
     * RaceClocker has no status field: a non-started or disqualified participant carries the status
     * as text in the result column, with `Result in seconds` left at `0`. Anything that is not a
     * parsable time is therefore a no-result reason.
     */
    val isTime: Boolean get() = result != null && timecodePattern.matches(result)

    val time: String? get() = result?.takeIf { isTime }

    val noResultReason: String? get() = result?.takeUnless { isTime }

    companion object {
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
