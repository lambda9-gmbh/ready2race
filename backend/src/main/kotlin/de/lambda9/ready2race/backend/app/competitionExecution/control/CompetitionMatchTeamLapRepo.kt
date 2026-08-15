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

    /**
     * Schreibt die Runden eines Boots, ohne ihren Erfassungszeitpunkt zu verlieren.
     *
     * Der Abruf ersetzte sie bis zum 15.08.2026 je Takt vollständig (Löschen + Einfügen mit
     * `created_at = now`). Damit trug jede Runde denselben, bei jedem Takt neuen Zeitstempel -
     * und das Rundenband des Livestreams, das "zuletzt eingetroffen" danach sortiert, hatte
     * nichts zu sortieren: Es fiel auf den Rundennamen zurück und zeigte ewig 1, 2, 3.
     *
     * Der Upsert trifft die Zeile über den eindeutigen Index (Boot, Position) und lässt
     * `created_at` stehen. Der Zeitstempel bedeutet damit, was sein Name sagt: wann diese Marke
     * zum ersten Mal da war. Name und Zeit werden nachgezogen - der Zeitnehmer darf eine Marke
     * umbenennen oder korrigieren.
     */
    fun upsert(records: List<CompetitionMatchTeamLapRecord>) = Jooq.query {
        with(COMPETITION_MATCH_TEAM_LAP) {
            records.forEach { record ->
                insertInto(this)
                    .set(record)
                    .onConflict(COMPETITION_MATCH_TEAM, POSITION)
                    .doUpdate()
                    .set(NAME, record.name)
                    .set(LAP_MILLIS, record.lapMillis)
                    .execute()
            }
        }
    }

    /**
     * Löscht die Marken jenseits von [keepPositions] - das Gegenstück zum Upsert, für den Fall,
     * dass der Zeitnehmer eine Marke wieder entfernt. Ohne das bliebe sie stehen, weil der Upsert
     * nur schreibt, was der Feed liefert.
     */
    fun deleteBeyond(teamId: UUID, keepPositions: Collection<Int>) =
        COMPETITION_MATCH_TEAM_LAP.delete {
            COMPETITION_MATCH_TEAM.eq(teamId).and(
                if (keepPositions.isEmpty()) org.jooq.impl.DSL.trueCondition()
                else POSITION.notIn(keepPositions)
            )
        }

    /** Alle Runden eines Boots - beim Zurücksetzen eines Laufs. */
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
