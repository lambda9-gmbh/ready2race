package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_GROUP_STATISTIC_EVALUATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionSetupMatchRepo {
    fun create(records: Collection<CompetitionSetupMatchRecord>) = COMPETITION_SETUP_MATCH.insert(records)

    fun get(setupMatchId: UUID) = COMPETITION_SETUP_MATCH.selectOne { ID.eq(setupMatchId) }

    // Overwrites name / execution order of a single setup match. Null arguments leave the respective field unchanged.
    fun updateNameAndOrder(id: UUID, name: String?, executionOrder: Int?) = COMPETITION_SETUP_MATCH.update(
        f = {
            if (name != null) this.name = name
            if (executionOrder != null) this.executionOrder = executionOrder
        },
        condition = { ID.eq(id) },
    )

    fun get(competitionSetupRoundIds: List<UUID>): JIO<List<CompetitionSetupMatchRecord>> = Jooq.query {
        with(COMPETITION_SETUP_MATCH) {
            selectFrom(this)
                .where(COMPETITION_SETUP_ROUND.`in`(competitionSetupRoundIds))
                .fetch()
        }
    }

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_SETUP_MATCH.select({ ID }) { ID.`in`(ids) }

    fun getAsJson(competitionSetupRoundIds: List<UUID>) =
        COMPETITION_SETUP_MATCH.selectAsJson { COMPETITION_SETUP_ROUND.`in`(competitionSetupRoundIds) }

    fun insertJsonData(data: String) = COMPETITION_SETUP_MATCH.insertJsonData(data)
}