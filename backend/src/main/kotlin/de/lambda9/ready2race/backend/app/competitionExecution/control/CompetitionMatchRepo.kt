package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.MatchForRunningStatusDto
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.*

object CompetitionMatchRepo {
    fun create(records: List<CompetitionMatchRecord>) = COMPETITION_MATCH.insert(records)

    fun exists(id: UUID) = COMPETITION_MATCH.exists { COMPETITION_SETUP_MATCH.eq(id) }

    fun update(id: UUID, f: CompetitionMatchRecord.() -> Unit) =
        COMPETITION_MATCH.update(f) { COMPETITION_SETUP_MATCH.eq(id) }

    fun delete(ids: List<UUID>) = COMPETITION_MATCH.delete { COMPETITION_SETUP_MATCH.`in`(ids) }

    fun getForStartList(id: UUID) = STARTLIST_VIEW.selectOne { ID.eq(id) }
    
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
                .`when`(DSL.exists(
                    selectOne()
                        .from(cmt)
                        .where(cmt.COMPETITION_MATCH.eq(cm.COMPETITION_SETUP_MATCH))
                        .and(cmt.PLACE.isNull)
                ), DSL.inline(false))
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