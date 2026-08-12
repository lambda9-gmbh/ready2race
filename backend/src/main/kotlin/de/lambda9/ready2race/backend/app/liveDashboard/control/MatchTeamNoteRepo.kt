package de.lambda9.ready2race.backend.app.liveDashboard.control

import de.lambda9.ready2race.backend.app.liveDashboard.entity.MatchTeamNoteDto
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.MatchTeamNoteRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.MATCH_TEAM_NOTE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object MatchTeamNoteRepo {

    fun create(record: MatchTeamNoteRecord) = MATCH_TEAM_NOTE.insert(record)

    /** Einträge sind unveränderlich - neben dem Anlegen gibt es nur das Löschen. */
    fun delete(noteId: UUID) = MATCH_TEAM_NOTE.delete { ID.eq(noteId) }

    /**
     * Die Zeile des Boots im Lauf, adressiert wie überall im Dashboard: über den Lauf und die
     * Meldung - dasselbe Paar, unter dem [LiveDashboardRepo.getTeams] die Teams führt
     * (`teamId` im DTO ist die Meldungs-Kennung, nicht die Zeilen-Kennung).
     *
     * Der Join bis zur Veranstaltung hält den Pfad ehrlich: eine Notiz lässt sich nicht über die
     * Veranstaltungs-Kennung einer fremden Veranstaltung anlegen.
     */
    fun findTeamRowId(eventId: UUID, matchId: UUID, registrationId: UUID) = Jooq.query {
        select(COMPETITION_MATCH_TEAM.ID)
            .from(COMPETITION_MATCH_TEAM)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
            .and(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(registrationId))
            .fetchAny(COMPETITION_MATCH_TEAM.ID)
    }

    /**
     * Ob diese Notiz zu genau dem Boot gehört, das der Pfad adressiert - das Löschen prüft das,
     * bevor es zugreift, sonst ließe sich mit einer erratenen Kennung quer über Läufe löschen.
     */
    fun existsForTeam(noteId: UUID, matchId: UUID, registrationId: UUID) = Jooq.query {
        fetchExists(
            selectOne()
                .from(MATCH_TEAM_NOTE)
                .join(COMPETITION_MATCH_TEAM)
                .on(MATCH_TEAM_NOTE.COMPETITION_MATCH_TEAM.eq(COMPETITION_MATCH_TEAM.ID))
                .where(MATCH_TEAM_NOTE.ID.eq(noteId))
                .and(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
                .and(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(registrationId))
        )
    }

    /**
     * Die Notizen mehrerer Läufe auf einen Schlag, gebündelt nach (Lauf, Meldung) - genau der
     * Schlüssel, mit dem das Dashboard seine Boote führt; dasselbe Muster wie
     * [de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchTeamLapRepo.getByMatches].
     * Älteste zuerst: die Liste liest sich im Dialog wie ein Gesprächsverlauf.
     */
    fun getByMatches(matchIds: Collection<UUID>) = Jooq.query {
        select(
            COMPETITION_MATCH_TEAM.COMPETITION_MATCH,
            COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
            MATCH_TEAM_NOTE.ID,
            MATCH_TEAM_NOTE.NOTE,
            MATCH_TEAM_NOTE.CREATED_AT,
            APP_USER.FIRSTNAME,
            APP_USER.LASTNAME,
        )
            .from(MATCH_TEAM_NOTE)
            .join(COMPETITION_MATCH_TEAM)
            .on(COMPETITION_MATCH_TEAM.ID.eq(MATCH_TEAM_NOTE.COMPETITION_MATCH_TEAM))
            // leftJoin: ein gelöschtes Konto nimmt der Notiz nur den Namen, nicht die Notiz.
            .leftJoin(APP_USER).on(APP_USER.ID.eq(MATCH_TEAM_NOTE.CREATED_BY))
            .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.`in`(matchIds))
            .orderBy(MATCH_TEAM_NOTE.CREATED_AT.asc(), MATCH_TEAM_NOTE.ID.asc())
            .fetch { r ->
                Triple(
                    r[COMPETITION_MATCH_TEAM.COMPETITION_MATCH]!!,
                    r[COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION]!!,
                    MatchTeamNoteDto(
                        id = r[MATCH_TEAM_NOTE.ID]!!,
                        note = r[MATCH_TEAM_NOTE.NOTE]!!,
                        createdAt = r[MATCH_TEAM_NOTE.CREATED_AT]!!,
                        author = r[APP_USER.FIRSTNAME]?.let { first -> "$first ${r[APP_USER.LASTNAME]}" },
                    ),
                )
            }
            .groupBy({ it.first to it.second }, { it.third })
    }
}
