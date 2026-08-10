package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamLapRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM_LAP
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import java.util.UUID

object CompetitionMatchTeamLapRepo {

    fun create(records: List<CompetitionMatchTeamLapRecord>) = COMPETITION_MATCH_TEAM_LAP.insert(records)

    /** Alle Runden eines Boots - der Abruf ersetzt sie je Takt vollständig (Löschen + Einfügen). */
    fun deleteByTeam(teamId: UUID) = COMPETITION_MATCH_TEAM_LAP.delete { COMPETITION_MATCH_TEAM.eq(teamId) }

    fun getByTeams(teamIds: List<UUID>) = COMPETITION_MATCH_TEAM_LAP.select { COMPETITION_MATCH_TEAM.`in`(teamIds) }
}
