package de.lambda9.ready2race.backend.app.eventDay.control

import de.lambda9.ready2race.backend.app.eventDay.entity.TimeslotSort
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.Timeslot
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.TimeslotWithCompetitionDurationDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.pagination.PaginationParameters
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object TimeslotRepo {

    private fun Timeslot.searchFields() = listOf(NAME, DESCRIPTION, START_TIME, END_TIME)
    fun getByEventDay(eventDayId: UUID) = TIMESLOT.select { EVENT_DAY.eq(eventDayId) }
    fun create(record: TimeslotRecord) = TIMESLOT.insertReturning(record) { ID }
    fun exists(id: UUID) = TIMESLOT.exists { ID.eq(id) }
    fun update(id: UUID, f: TimeslotRecord.() -> Unit) = TIMESLOT.update(f) { ID.eq(id) }
    fun delete(id: UUID) = TIMESLOT.delete { ID.eq(id) }
    fun getTimeslot(id: UUID) = TIMESLOT.selectOne { ID.eq(id) }

    fun countByEventDay(
        eventDayId: UUID,
        search: String?,
    ): JIO<Int> = Jooq.query {
        with(TIMESLOT) {
            fetchCount(
                this,
                DSL.and(
                    TIMESLOT.EVENT_DAY.eq(eventDayId),
                    search.metaSearch(searchFields())
                )
            )
        }
    }

    fun pageByEventDay(
        eventDayId: UUID,
        params: PaginationParameters<TimeslotSort>,
    ): JIO<List<TimeslotRecord>> = Jooq.query {
        with(TIMESLOT) {
            selectFrom(this)
                .page(params, searchFields()) {
                    EVENT_DAY.eq(eventDayId)
                }
                .fetch()
        }
    }

    fun timeslotForCompetitionUnitExists(id: UUID?, eventDayId: UUID): JIO<Boolean> =
        TIMESLOT
            .exists {
                TIMESLOT.EVENT_DAY.eq(eventDayId).and(
                    TIMESLOT.MATCH_REFERENCE.eq(id)
                        .or(
                            TIMESLOT.ROUND_REFERENCE.eq(id)
                                .and(TIMESLOT.MATCH_REFERENCE.isNull)
                        )
                        .or(
                            TIMESLOT.COMPETITION_REFERENCE.eq(id)
                                .and(TIMESLOT.MATCH_REFERENCE.isNull)
                                .and(TIMESLOT.ROUND_REFERENCE.isNull)
                        )
                )
            }

    fun findSelfIncludingTimeslotById(id: UUID?): JIO<TimeslotWithCompetitionDurationDataRecord?> =
        TIMESLOT_WITH_COMPETITION_DURATION_DATA
            .selectOne { MATCH_REFERENCE.eq(id)
                    .or(ROUND_REFERENCE.eq(id)
                        .and(MATCH_REFERENCE.isNull))
                    .or(COMPETITION_REFERENCE.eq(id)
                        .and(MATCH_REFERENCE.isNull)
                        .and(ROUND_REFERENCE.isNull))
            }

    fun lowerCompetitionUnitTimeslotAlreadyExists(id: UUID?, eventDayId: UUID): JIO<Boolean> =
        TIMESLOT
            .exists {
                TIMESLOT.EVENT_DAY.eq(eventDayId).and(
                    DSL.or(
                        TIMESLOT.ROUND_REFERENCE.eq(id).and(TIMESLOT.MATCH_REFERENCE.isNotNull),
                        TIMESLOT.COMPETITION_REFERENCE.eq(id).and(TIMESLOT.ROUND_REFERENCE.isNotNull)
                    )
                )
            }

    fun higherCompetitionUnitTimeslotAlreadyExists(
        eventDayId: UUID,
        competitionReference: UUID?,
        roundReference: UUID?,
    ): JIO<Boolean> {
        val competitionCondition =
            if (competitionReference != null) {
                TIMESLOT.COMPETITION_REFERENCE.eq(competitionReference)
                    .and(TIMESLOT.ROUND_REFERENCE.isNull)
                    .and(TIMESLOT.MATCH_REFERENCE.isNull)
            } else {
                DSL.falseCondition()
            }

        val roundCondition =
            if (roundReference != null) {
                TIMESLOT.ROUND_REFERENCE.eq(roundReference)
                    .and(TIMESLOT.MATCH_REFERENCE.isNull)
            } else {
                DSL.falseCondition()
            }

        return TIMESLOT.exists {
            TIMESLOT.EVENT_DAY.eq(eventDayId).and(
                DSL.or(
                    competitionCondition,
                    roundCondition
                )
            )
        }
    }

}
