package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object CompetitionSetupMatchRepo {
    fun create(records: Collection<CompetitionSetupMatchRecord>) = COMPETITION_SETUP_MATCH.insert(records)

    fun get(setupMatchId: UUID) = COMPETITION_SETUP_MATCH.selectOne { ID.eq(setupMatchId) }

    fun get(competitionSetupRoundIds: List<UUID>): JIO<List<CompetitionSetupMatchRecord>> = Jooq.query {
        with(COMPETITION_SETUP_MATCH) {
            selectFrom(this)
                .where(COMPETITION_SETUP_ROUND.`in`(competitionSetupRoundIds))
                .fetch()
        }
    }
}