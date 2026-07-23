package de.lambda9.ready2race.backend.app.raceclocker.entity

import de.lambda9.ready2race.backend.validation.timecodePattern
import java.util.UUID

/**
 * One participant row of a RaceClocker results feed, reduced to the fields this integration needs.
 *
 * Deliberately not modelled: `Result in seconds` (redundant with [result]), the finish/start
 * timestamps (their key is localised - `Finish` vs `Ziel`) and `Penalty`. The penalty is already
 * baked into [result] by RaceClocker, so adding it again would double-count it.
 */
data class RaceClockerFeedRow(
    val name: String,
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
) {
    /**
     * RaceClocker has no status field: a non-started or disqualified participant carries the status
     * as text in the result column, with `Result in seconds` left at `0`. Anything that is not a
     * parsable time is therefore a no-result reason.
     */
    val isTime: Boolean get() = result != null && timecodePattern.matches(result)

    val time: String? get() = result?.takeIf { isTime }

    val noResultReason: String? get() = result?.takeUnless { isTime }
}
