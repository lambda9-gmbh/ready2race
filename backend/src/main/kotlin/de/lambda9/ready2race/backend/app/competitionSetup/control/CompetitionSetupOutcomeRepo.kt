package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupOutcomeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_OUTCOME
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionSetupOutcomeRepo {
    fun create(records: Collection<CompetitionSetupOutcomeRecord>) =
        COMPETITION_SETUP_OUTCOME.insert(records)

    fun get(competitionSetupMatchOrGroupIds: List<UUID>): JIO<List<CompetitionSetupOutcomeRecord>> = Jooq.query {
        with(COMPETITION_SETUP_OUTCOME) {
            selectFrom(this)
                .where(
                    COMPETITION_SETUP_MATCH.`in`(competitionSetupMatchOrGroupIds)
                        .or(COMPETITION_SETUP_GROUP.`in`(competitionSetupMatchOrGroupIds))
                )
                .fetch()
        }
    }
}