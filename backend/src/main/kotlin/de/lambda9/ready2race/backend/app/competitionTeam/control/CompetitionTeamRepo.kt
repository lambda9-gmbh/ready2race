package de.lambda9.ready2race.backend.app.competitionTeam.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_TEAM
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionTeamRepo {
    fun get(matchIds: List<UUID>): JIO<List<CompetitionTeamRecord>> = Jooq.query {
        with(COMPETITION_TEAM) {
            selectFrom(this)
                .where(COMPETITION_MATCH.`in`(matchIds))
                .fetch()
        }
    }
}