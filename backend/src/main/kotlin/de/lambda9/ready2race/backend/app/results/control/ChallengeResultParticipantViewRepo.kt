package de.lambda9.ready2race.backend.app.results.control

import de.lambda9.ready2race.backend.database.generated.tables.references.CHALLENGE_RESULT_PARTICIPANT_VIEW
import de.lambda9.ready2race.backend.database.select
import java.util.*

object ChallengeResultParticipantViewRepo {

    fun get(eventId: UUID, competitionId: UUID?, ratingCategory: UUID?) =
        CHALLENGE_RESULT_PARTICIPANT_VIEW.select {
            val conditions = mutableListOf(EVENT_ID.eq(eventId))
            competitionId?.let { conditions.add(COMPETITION_ID.eq(it)) }
            ratingCategory?.let { conditions.add(RATING_CATEGORY_ID.eq(it)) }
            conditions.reduce { acc, condition -> acc.and(condition) }
        }
}