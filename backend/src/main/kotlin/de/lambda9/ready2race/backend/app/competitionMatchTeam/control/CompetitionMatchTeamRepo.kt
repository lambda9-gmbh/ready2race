package de.lambda9.ready2race.backend.app.competitionMatchTeam.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionMatchTeamRepo {
    fun get(matchIds: List<UUID>): JIO<List<CompetitionMatchTeamRecord>> = Jooq.query {
        with(COMPETITION_MATCH_TEAM) {
            selectFrom(this)
                .where(COMPETITION_MATCH.`in`(matchIds))
                .fetch()
        }
    }

    fun create(records: List<CompetitionMatchTeamRecord>) = COMPETITION_MATCH_TEAM.insert(records)

    fun updateByMatchAndRegistrationId(matchId: UUID, registrationId: UUID, f: CompetitionMatchTeamRecord.() -> Unit) =
        COMPETITION_MATCH_TEAM.update(f) {
            COMPETITION_MATCH.eq(matchId).and(COMPETITION_REGISTRATION.eq(registrationId))
        }
}