package de.lambda9.ready2race.backend.app.results.entity

import de.lambda9.ready2race.backend.pagination.Sortable
import org.jooq.Field

enum class ResultsChallengeParticipantSort : Sortable {
    RANK,
    NAME;

    override fun toFields(): List<Field<*>> = emptyList()

    fun comparator(): Comparator<ResultChallengeParticipantDto> = when (this) {
        RANK -> compareBy { it.rank }
        NAME -> compareBy({ it.lastName }, { it.firstName })
    }
}