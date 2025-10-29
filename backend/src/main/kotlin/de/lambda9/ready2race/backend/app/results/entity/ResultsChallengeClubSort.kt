package de.lambda9.ready2race.backend.app.results.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class ResultsChallengeClubSort : Sortable {
    TOTAL_RANK,
    RELATIVE_RANK;

    override fun toFields(): List<Field<*>> = emptyList()

    fun comparator(): Comparator<ResultChallengeClubDto> = when (this) {
        TOTAL_RANK -> compareBy { it.totalRank }
        RELATIVE_RANK -> compareBy { it.relativeRank }
    }
}