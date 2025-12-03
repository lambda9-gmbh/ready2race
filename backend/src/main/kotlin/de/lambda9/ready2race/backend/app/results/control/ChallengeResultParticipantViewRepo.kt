package de.lambda9.ready2race.backend.app.results.control

import de.lambda9.ready2race.backend.database.generated.tables.references.CHALLENGE_RESULT_PARTICIPANT_VIEW
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.select
import org.jooq.impl.DSL
import java.util.*

object ChallengeResultParticipantViewRepo {

    fun get(eventId: UUID, competitionId: UUID?, ratingCategory: UUID?, verifiedIfNeededOnly: Boolean) =
        CHALLENGE_RESULT_PARTICIPANT_VIEW.select {
            val conditions = mutableListOf(EVENT_ID.eq(eventId))
            competitionId?.let { conditions.add(COMPETITION_ID.eq(it)) }
            ratingCategory?.let { conditions.add(RATING_CATEGORY_ID.eq(it)) }
            if (verifiedIfNeededOnly) {
                conditions.add(
                    RESULT_VERIFIED_AT.isNotNull.or(
                        DSL.exists(
                            DSL.selectOne().from(EVENT)
                                .where(EVENT.ID.eq(EVENT_ID))
                                .and(EVENT.SUBMISSION_NEEDS_VERIFICATION.isFalse)
                        )
                    )
                )
            }
            conditions.reduce { acc, condition -> acc.and(condition) }
        }
}