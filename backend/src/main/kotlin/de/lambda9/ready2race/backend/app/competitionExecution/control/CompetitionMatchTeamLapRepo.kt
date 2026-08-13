package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.matchTeamLapDto
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamLapRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM_LAP
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.select
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CompetitionMatchTeamLapRepo {

    fun create(records: List<CompetitionMatchTeamLapRecord>) = COMPETITION_MATCH_TEAM_LAP.insert(records)

    /** Alle Runden eines Boots - der Abruf ersetzt sie je Takt vollständig (Löschen + Einfügen). */
    fun deleteByTeam(teamId: UUID) = COMPETITION_MATCH_TEAM_LAP.delete { COMPETITION_MATCH_TEAM.eq(teamId) }

    fun getByTeams(teamIds: List<UUID>) = COMPETITION_MATCH_TEAM_LAP.select { COMPETITION_MATCH_TEAM.`in`(teamIds) }

    /**
     * Die Zwischenzeiten mehrerer Läufe auf einen Schlag, gebündelt nach (Lauf, Meldung) - genau der
     * Schlüssel, mit dem die Anzeigen (Schiedsrichter-Dashboard, Boards) ihre Boote führen. So holt
     * jede Ansicht die Laps ihrer sichtbaren Läufe mit einer Abfrage, statt je Boot einzeln.
     * Aufsteigend nach der Marken-Reihenfolge (`position`), fertig als Anzeige-Text.
     */
    fun getByMatches(matchIds: Collection<UUID>) =
        Jooq.query {
            select(
                COMPETITION_MATCH_TEAM.COMPETITION_MATCH,
                COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
                COMPETITION_MATCH_TEAM_LAP.NAME,
                COMPETITION_MATCH_TEAM_LAP.LAP_MILLIS,
                COMPETITION_MATCH_TEAM_LAP.CREATED_AT,
            )
                .from(COMPETITION_MATCH_TEAM_LAP)
                .join(COMPETITION_MATCH_TEAM)
                .on(COMPETITION_MATCH_TEAM.ID.eq(COMPETITION_MATCH_TEAM_LAP.COMPETITION_MATCH_TEAM))
                .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.`in`(matchIds))
                .orderBy(COMPETITION_MATCH_TEAM_LAP.POSITION.asc())
                .fetch { r ->
                    Triple(
                        r[COMPETITION_MATCH_TEAM.COMPETITION_MATCH]!!,
                        r[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION]!!,
                        matchTeamLapDto(
                            r[COMPETITION_MATCH_TEAM_LAP.NAME]!!,
                            r[COMPETITION_MATCH_TEAM_LAP.LAP_MILLIS]!!,
                            r[COMPETITION_MATCH_TEAM_LAP.CREATED_AT],
                        ),
                    )
                }
                .groupBy({ it.first to it.second }, { it.third })
        }
}
