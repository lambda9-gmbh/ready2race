package de.lambda9.ready2race.backend.app.competitionSetup.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionSetupRoundWithMatchesRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND_WITH_MATCHES
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionSetupRoundRepo {
    fun create(records: Collection<CompetitionSetupRoundRecord>) = COMPETITION_SETUP_ROUND.insert(records)

    fun delete(key: UUID) = COMPETITION_SETUP_ROUND.delete {
        COMPETITION_SETUP.eq(key).or(COMPETITION_SETUP_TEMPLATE.eq(key))
    }

    fun get(id: UUID) = COMPETITION_SETUP_ROUND.selectOne { ID.eq(id) }

    fun getBySetupId(key: UUID): JIO<List<CompetitionSetupRoundRecord>> = Jooq.query {
        with(COMPETITION_SETUP_ROUND) {
            selectFrom(this)
                .where(COMPETITION_SETUP.eq(key).or(COMPETITION_SETUP_TEMPLATE.eq(key)))
                .fetch()
        }
    }

    fun getWithMatchesBySetup(setupId: UUID): JIO<List<CompetitionSetupRoundWithMatchesRecord>> = Jooq.query {
        with(COMPETITION_SETUP_ROUND_WITH_MATCHES) {
            selectFrom(this)
                .where(COMPETITION_SETUP.eq(setupId))
                .fetch()
        }
    }

    fun getWithMatches(id: UUID) = COMPETITION_SETUP_ROUND_WITH_MATCHES.selectOne { SETUP_ROUND_ID.eq(id) }
}