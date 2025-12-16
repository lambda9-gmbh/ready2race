package de.lambda9.ready2race.backend.app.results.control

import de.lambda9.ready2race.backend.database.generated.tables.references.CHALLENGE_RESULT_PARTICIPANT_VIEW
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object ChallengeResultParticipantViewRepo {

    fun getByEventIdAndParticipantId(eventId: UUID, participantId: UUID, verifiedIfNeededOnly: Boolean) =
        CHALLENGE_RESULT_PARTICIPANT_VIEW.select {
            DSL.and(
                EVENT_ID.eq(eventId),
                ID.eq(participantId),
                if (verifiedIfNeededOnly) {
                    DSL.or(
                        RESULT_VERIFIED_AT.isNotNull,
                        DSL.exists(
                            DSL.selectOne().from(EVENT)
                                .where(EVENT.ID.eq(EVENT_ID))
                                .and(EVENT.SUBMISSION_NEEDS_VERIFICATION.isFalse)
                        )
                    )
                } else DSL.trueCondition()
            )
        }

    fun getByEventIdAndClubId(eventId: UUID, clubId: UUID, verifiedIfNeededOnly: Boolean) =
        CHALLENGE_RESULT_PARTICIPANT_VIEW.select {
            DSL.and(
                EVENT_ID.eq(eventId),
                CLUB_ID.eq(clubId),
                if (verifiedIfNeededOnly) {
                    DSL.or(
                        RESULT_VERIFIED_AT.isNotNull,
                        DSL.exists(
                            DSL.selectOne().from(EVENT)
                                .where(EVENT.ID.eq(EVENT_ID))
                                .and(EVENT.SUBMISSION_NEEDS_VERIFICATION.isFalse)
                        )
                    )
                } else DSL.trueCondition()
            )
        }

    fun getForCertificates(eventId: UUID) = Jooq.query {
        with (CHALLENGE_RESULT_PARTICIPANT_VIEW) {
            selectDistinct(ID)
                .from(this)
                .where(
                    DSL.and(
                        EVENT_ID.eq(eventId),
                        EMAIL.isNotNull,
                        DSL.or(
                            RESULT_VERIFIED_AT.isNotNull,
                            DSL.exists(
                                DSL.selectOne().from(EVENT)
                                    .where(EVENT.ID.eq(EVENT_ID))
                                    .and(EVENT.SUBMISSION_NEEDS_VERIFICATION.isFalse)
                            )
                        )
                    )
                )
                .fetch { it.value1() }
        }
    }

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