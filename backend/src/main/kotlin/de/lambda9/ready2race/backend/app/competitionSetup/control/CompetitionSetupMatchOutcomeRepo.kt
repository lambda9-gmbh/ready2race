package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchOutcomeRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH_OUTCOME
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionSetupMatchOutcomeRepo {
    fun create(records: Collection<CompetitionSetupMatchOutcomeRecord>) =
        COMPETITION_SETUP_MATCH_OUTCOME.insert(records)

    fun get(competitionSetupMatchIds: List<UUID>): JIO<List<CompetitionSetupMatchOutcomeRecord>> = Jooq.query {
        with(COMPETITION_SETUP_MATCH_OUTCOME) {
            selectFrom(this)
                .where(COMPETITION_SETUP_MATCH.`in`(competitionSetupMatchIds))
                .fetch()
        }
    }
}