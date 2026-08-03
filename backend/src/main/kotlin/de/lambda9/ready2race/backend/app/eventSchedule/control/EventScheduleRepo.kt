package de.lambda9.ready2race.backend.app.eventSchedule.control

import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import org.jooq.impl.DSL.selectOne
import java.util.UUID

object EventScheduleRepo {

    /**
     * Alle Slots des Events mit dem Kontext für die Zustandsableitung. "Runde materialisiert" =
     * mindestens eine Setup-Zeile derselben Runde hat einen Lauf. Dafür braucht die korrelierte
     * Subquery eigene Aliase für competition_setup_match/competition_match, weil beide Tabellen
     * im äußeren Query bereits unaliast verjoint sind.
     */
    fun getSlots(eventId: UUID) = Jooq.query {
        val sibling = COMPETITION_SETUP_MATCH.`as`("sibling")
        val siblingMatch = COMPETITION_MATCH.`as`("sibling_match")

        val roundMaterialized = DSL.field(
            DSL.exists(
                selectOne()
                    .from(sibling)
                    .join(siblingMatch)
                    .on(siblingMatch.COMPETITION_SETUP_MATCH.eq(sibling.ID))
                    .where(sibling.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            )
        ).`as`("round_materialized")

        select(
            EVENT_SCHEDULE_SLOT.asterisk(),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH.isNotNull.`as`("match_exists"),
            COMPETITION_MATCH.STARTED_AT.`as`("match_started_at"),
            COMPETITION_MATCH.FINISHED_AT.`as`("match_finished_at"),
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            roundMaterialized,
        )
            .from(EVENT_SCHEDULE_SLOT)
            .leftJoin(COMPETITION_SETUP_MATCH)
            .on(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .leftJoin(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .leftJoin(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .leftJoin(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH))
            .where(EVENT_SCHEDULE_SLOT.EVENT.eq(eventId))
            .orderBy(EVENT_SCHEDULE_SLOT.START_TIME.asc(), COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc())
            .fetch()
    }

    /** Setup-Zeilen des Events ohne Slot — die "nicht verplant"-Liste. */
    fun getUnplannedSetupMatches(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_SETUP_MATCH.ID,
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
        )
            .from(COMPETITION_SETUP_MATCH)
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .andNotExists(
                selectOne()
                    .from(EVENT_SCHEDULE_SLOT)
                    .where(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            )
            .orderBy(COMPETITION_PROPERTIES.NAME, COMPETITION_SETUP_MATCH.EXECUTION_ORDER)
            .fetch()
    }
}
