package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupGroupRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_GROUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_PLACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertJsonData
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectAsJson
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionSetupGroupRepo {
    fun create(records: Collection<CompetitionSetupGroupRecord>) = COMPETITION_SETUP_GROUP.insert(records)

    fun get(competitionSetupGroupIds: List<UUID>): JIO<List<CompetitionSetupGroupRecord>> = Jooq.query {
        with(COMPETITION_SETUP_GROUP) {
            selectFrom(this)
                .where(ID.`in`(competitionSetupGroupIds))
                .fetch()
        }
    }

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_SETUP_GROUP.select({ ID }) { ID.`in`(ids) }

    fun getIds(competitionSetupGroupIds: List<UUID>) =
        COMPETITION_SETUP_GROUP.select({ ID }) { ID.`in`(competitionSetupGroupIds) }

    fun getAsJson(competitionSetupGroupIds: List<UUID>) =
        COMPETITION_SETUP_GROUP.selectAsJson { ID.`in`(competitionSetupGroupIds) }

    fun insertJsonData(data: String) = COMPETITION_SETUP_GROUP.insertJsonData(data)
}