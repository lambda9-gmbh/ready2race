package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupParticipantRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_GROUP
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_PARTICIPANT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.insertJsonData
import de.lambda9.ready2race.backend.database.select
import de.lambda9.ready2race.backend.database.selectAsJson
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionSetupParticipantRepo {
    fun create(records: Collection<CompetitionSetupParticipantRecord>) =
        COMPETITION_SETUP_PARTICIPANT.insert(records)

    fun get(competitionSetupMatchOrGroupIds: List<UUID>): JIO<List<CompetitionSetupParticipantRecord>> = Jooq.query {
        with(COMPETITION_SETUP_PARTICIPANT) {
            selectFrom(this)
                .where(
                    COMPETITION_SETUP_MATCH.`in`(competitionSetupMatchOrGroupIds)
                        .or(COMPETITION_SETUP_GROUP.`in`(competitionSetupMatchOrGroupIds))
                )
                .fetch()
        }
    }

    fun getOverlapIds(ids: List<UUID>) = COMPETITION_SETUP_PARTICIPANT.select({ ID }) { ID.`in`(ids) }

    fun getAsJson(competitionSetupMatchOrGroupIds: List<UUID>) = COMPETITION_SETUP_PARTICIPANT.selectAsJson {
        COMPETITION_SETUP_MATCH.`in`(competitionSetupMatchOrGroupIds)
            .or(COMPETITION_SETUP_GROUP.`in`(competitionSetupMatchOrGroupIds))
    }

    fun insertJsonData(data: String) = COMPETITION_SETUP_PARTICIPANT.insertJsonData(data)
}