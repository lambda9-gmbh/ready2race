package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.MatchForRunningStatusDto
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Record10
import org.jooq.Record12
import org.jooq.Result
import org.jooq.impl.DSL
import org.jooq.impl.DSL.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object CompetitionMatchRepo {
    fun create(records: List<CompetitionMatchRecord>) = COMPETITION_MATCH.insert(records)

    fun exists(id: UUID) = COMPETITION_MATCH.exists { COMPETITION_SETUP_MATCH.eq(id) }

    fun update(id: UUID, f: CompetitionMatchRecord.() -> Unit) =
        COMPETITION_MATCH.update(f) { COMPETITION_SETUP_MATCH.eq(id) }

    fun delete(ids: List<UUID>) = COMPETITION_MATCH.delete { COMPETITION_SETUP_MATCH.`in`(ids) }

    fun getForStartList(id: UUID) = STARTLIST_VIEW.selectOne { ID.eq(id) }

    fun getMatchResults(
        eventId: UUID,
        eventDayId: UUID?,
        competitionId: UUID?,
        limit: Int
    ) = Jooq.query {
        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.UPDATED_AT,
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
            EVENT_DAY.ID.`as`("event_day_id"),
            EVENT_DAY.DATE.`as`("event_day_date"),
            EVENT_DAY.NAME.`as`("event_day_name")
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY_HAS_COMPETITION).on(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY).on(EVENT_DAY.ID.eq(EVENT_DAY_HAS_COMPETITION.EVENT_DAY))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(
                exists(
                    selectOne()
                        .from(COMPETITION_MATCH_TEAM)
                        .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                        .and(COMPETITION_MATCH_TEAM.PLACE.isNotNull)
                )
            )
            .and(
                notExists(
                    selectOne()
                        .from(COMPETITION_MATCH_TEAM)
                        .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                        .and(COMPETITION_MATCH_TEAM.PLACE.isNull)
                )
            )
            .and(
                field(
                    select(count())
                        .from(COMPETITION_MATCH_TEAM)
                        .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                ).gt(1)
            )
            .apply {
                if (eventDayId != null) {
                    and(EVENT_DAY.ID.eq(eventDayId))
                }
                if (competitionId != null) {
                    and(COMPETITION.ID.eq(competitionId))
                }
            }
            .orderBy(COMPETITION_MATCH.UPDATED_AT.desc())
            .limit(limit)
            .fetch()
    }

    fun getRunningMatches(
        eventId: UUID,
        eventDayId: UUID?,
        competitionId: UUID?,
        limit: Int
    ) = Jooq.query {
        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
            EVENT_DAY.ID.`as`("event_day_id"),
            EVENT_DAY.DATE.`as`("event_day_date"),
            EVENT_DAY.NAME.`as`("event_day_name")
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY_HAS_COMPETITION).on(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY).on(EVENT_DAY.ID.eq(EVENT_DAY_HAS_COMPETITION.EVENT_DAY))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.CURRENTLY_RUNNING.eq(true))
            .apply {
                if (eventDayId != null) {
                    and(EVENT_DAY.ID.eq(eventDayId))
                }
                if (competitionId != null) {
                    and(COMPETITION.ID.eq(competitionId))
                }
            }
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .limit(limit)
            .fetch()
    }

    fun getUpcomingMatches(
        eventId: UUID,
        eventDayId: UUID?,
        competitionId: UUID?,
        roundName: String?,
        limit: Int
    ) = Jooq.query {
        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_MATCH.START_TIME_OFFSET,
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
            EVENT_DAY.ID.`as`("event_day_id"),
            EVENT_DAY.DATE.`as`("event_day_date"),
            EVENT_DAY.NAME.`as`("event_day_name")
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY_HAS_COMPETITION).on(EVENT_DAY_HAS_COMPETITION.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(EVENT_DAY).on(EVENT_DAY.ID.eq(EVENT_DAY_HAS_COMPETITION.EVENT_DAY))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.START_TIME.isNotNull)
            .and(COMPETITION_MATCH.START_TIME.gt(LocalDateTime.now()))
            .apply {
                if (eventDayId != null) {
                    and(EVENT_DAY.ID.eq(eventDayId))
                }
                if (competitionId != null) {
                    and(COMPETITION.ID.eq(competitionId))
                }
                if (roundName != null) {
                    and(COMPETITION_SETUP_ROUND.NAME.eq(roundName))
                }
            }
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .limit(limit)
            .fetch()
    }

    fun getMatchesByEvent(
        eventId: UUID,
        currentlyRunning: Boolean? = null,
        withoutPlaces: Boolean? = null
    ): JIO<List<MatchForRunningStatusDto>> = Jooq.query {
        val cm = COMPETITION_MATCH
        val csm = COMPETITION_SETUP_MATCH
        val csr = COMPETITION_SETUP_ROUND
        val cs = COMPETITION_SETUP
        val cp = COMPETITION_PROPERTIES
        val c = COMPETITION
        val cmt = COMPETITION_MATCH_TEAM

        var query = select(
            cm.COMPETITION_SETUP_MATCH,
            c.ID,
            cp.NAME,
            DSL.denseRank()
                .over(DSL.partitionBy(c.ID).orderBy(csr.ID))
                .`as`("round_number"),
            csr.NAME,
            DSL.rowNumber()
                .over(DSL.partitionBy(csr.ID).orderBy(csm.EXECUTION_ORDER))
                .`as`("match_number"),
            csm.NAME,
            DSL.case_()
                .`when`(
                    DSL.exists(
                        selectOne()
                            .from(cmt)
                            .where(cmt.COMPETITION_MATCH.eq(cm.COMPETITION_SETUP_MATCH))
                            .and(cmt.PLACE.isNull)
                    ), DSL.inline(false)
                )
                .otherwise(DSL.inline(true))
                .`as`("has_places_set"),
            cm.CURRENTLY_RUNNING,
            cm.START_TIME
        )
            .from(cm)
            .join(csm).on(cm.COMPETITION_SETUP_MATCH.eq(csm.ID))
            .join(csr).on(csm.COMPETITION_SETUP_ROUND.eq(csr.ID))
            .join(cs).on(csr.COMPETITION_SETUP.eq(cs.COMPETITION_PROPERTIES))
            .join(cp).on(cs.COMPETITION_PROPERTIES.eq(cp.ID))
            .join(c).on(cp.COMPETITION.eq(c.ID))
            .where(c.EVENT.eq(eventId))

        if (currentlyRunning != null) {
            query = query.and(cm.CURRENTLY_RUNNING.eq(currentlyRunning))
        }

        if (withoutPlaces == true) {
            query = query.and(
                DSL.exists(
                    selectOne()
                        .from(cmt)
                        .where(cmt.COMPETITION_MATCH.eq(cm.COMPETITION_SETUP_MATCH))
                        .and(cmt.PLACE.isNull)
                )
            )
        }

        query.fetch { record ->
            MatchForRunningStatusDto(
                id = record.value1()!!,
                competitionId = record.value2()!!,
                competitionName = record.value3()!!,
                roundNumber = record.value4()!!,
                roundName = record.value5()!!,
                matchNumber = record.value6()!!,
                matchName = record.value7(),
                hasPlacesSet = record.value8()!!,
                currentlyRunning = record.value9()!!,
                startTime = record.value10()
            )
        }
    }
}