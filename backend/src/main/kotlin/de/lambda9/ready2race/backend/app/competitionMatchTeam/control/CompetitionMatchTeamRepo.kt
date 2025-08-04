package de.lambda9.ready2race.backend.app.competitionMatchTeam.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchTeamRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import de.lambda9.ready2race.backend.database.updateMany
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.*
import java.util.UUID

object CompetitionMatchTeamRepo {
    fun get(matchIds: List<UUID>): JIO<List<CompetitionMatchTeamRecord>> = Jooq.query {
        with(COMPETITION_MATCH_TEAM) {
            selectFrom(this)
                .where(COMPETITION_MATCH.`in`(matchIds))
                .fetch()
        }
    }

    fun getByMatch(matchId: UUID): JIO<List<CompetitionMatchTeamRecord>> = Jooq.query {
        with(COMPETITION_MATCH_TEAM) {
            selectFrom(this)
                .where(COMPETITION_MATCH.eq(matchId))
                .fetch()
        }
    }

    fun getByMatchAndRegistration(matchId: UUID, competitionRegistrationId: UUID) = COMPETITION_MATCH_TEAM.selectOne {
        COMPETITION_MATCH.eq(matchId).and(COMPETITION_REGISTRATION.eq(competitionRegistrationId))
    }

    fun getById(id: UUID) = COMPETITION_MATCH_TEAM.selectOne { ID.eq(id) }

    fun create(records: List<CompetitionMatchTeamRecord>) = COMPETITION_MATCH_TEAM.insert(records)

    fun update(record: CompetitionMatchTeamRecord, f: CompetitionMatchTeamRecord.() -> Unit) =
        COMPETITION_MATCH_TEAM.update(record, f)

    fun updateByMatchAndRegistrationId(matchId: UUID, registrationId: UUID, f: CompetitionMatchTeamRecord.() -> Unit) =
        COMPETITION_MATCH_TEAM.update(f) {
            COMPETITION_MATCH.eq(matchId).and(COMPETITION_REGISTRATION.eq(registrationId))
        }

    fun getTeamsForMatchResult(matchId: UUID) =
        Jooq.query {
            select(
                COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
                COMPETITION_MATCH_TEAM.PLACE,
                COMPETITION_REGISTRATION.NAME.`as`("team_name"),
                CLUB.NAME.`as`("club_name"),
                PARTICIPANT.ID.`as`("participant_id"),
                PARTICIPANT.FIRSTNAME,
                PARTICIPANT.LASTNAME,
                NAMED_PARTICIPANT.NAME.`as`("named_role")
            )
                .from(COMPETITION_MATCH_TEAM)
                .join(COMPETITION_REGISTRATION)
                .on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
                .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
                .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
                .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
                .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
                .leftJoin(NAMED_PARTICIPANT)
                .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
                .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
                .and(COMPETITION_MATCH_TEAM.PLACE.isNotNull)
                .orderBy(COMPETITION_MATCH_TEAM.PLACE.asc())
                .fetch()
        }

    fun getTeamsForUpcomingMatch(matchId: UUID) =
        Jooq.query {
            select(
                COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
                COMPETITION_MATCH_TEAM.START_NUMBER,
                COMPETITION_REGISTRATION.NAME.`as`("team_name"),
                CLUB.NAME.`as`("club_name"),
                PARTICIPANT.ID.`as`("participant_id"),
                PARTICIPANT.FIRSTNAME,
                PARTICIPANT.LASTNAME,
                PARTICIPANT.YEAR,
                PARTICIPANT.GENDER,
                PARTICIPANT.EXTERNAL_CLUB_NAME,
                NAMED_PARTICIPANT.NAME.`as`("named_role")
            )
                .from(COMPETITION_MATCH_TEAM)
                .join(COMPETITION_REGISTRATION)
                .on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
                .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
                .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
                .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
                .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
                .leftJoin(NAMED_PARTICIPANT)
                .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
                .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
                .orderBy(
                    COMPETITION_MATCH_TEAM.START_NUMBER.asc().nullsLast(),
                    COMPETITION_REGISTRATION.NAME.asc().nullsLast()
                )
                .fetch()
        }

    fun getTeamForRunningMatch(matchId: UUID) =
        Jooq.query {
            select(
                COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
                COMPETITION_MATCH_TEAM.START_NUMBER,
                COMPETITION_MATCH_TEAM.PLACE,
                COMPETITION_REGISTRATION.NAME.`as`("team_name"),
                CLUB.NAME.`as`("club_name"),
                PARTICIPANT.ID.`as`("participant_id"),
                PARTICIPANT.FIRSTNAME,
                PARTICIPANT.LASTNAME,
                PARTICIPANT.YEAR,
                PARTICIPANT.GENDER,
                PARTICIPANT.EXTERNAL_CLUB_NAME,
                NAMED_PARTICIPANT.NAME.`as`("named_role")
            )
                .from(COMPETITION_MATCH_TEAM)
                .join(COMPETITION_REGISTRATION)
                .on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
                .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
                .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
                .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
                .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
                .leftJoin(NAMED_PARTICIPANT)
                .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
                .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
                .orderBy(
                    COMPETITION_MATCH_TEAM.START_NUMBER.asc().nullsLast(),
                    COMPETITION_REGISTRATION.NAME.asc().nullsLast()
                )
                .fetch()
        }

}