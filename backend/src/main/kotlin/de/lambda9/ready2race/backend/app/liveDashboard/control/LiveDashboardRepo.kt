package de.lambda9.ready2race.backend.app.liveDashboard.control

import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import org.jooq.impl.DSL.selectOne
import java.time.LocalDateTime
import java.util.UUID

object LiveDashboardRepo {

    fun getMatches(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.STARTED_AT,
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            COMPETITION_MATCH.FINISHED_AT,
            COMPETITION_MATCH.RACECLOCKER_POLLED_AT,
            COMPETITION_MATCH.RACECLOCKER_POLL_ERROR,
            COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT,
            COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc().nullsLast(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .fetch()
    }

    /**
     * Ohne [matchId]/[registrationId] die Mannschaften der ganzen Veranstaltung, mit ihnen die
     * einer einzelnen — der Detail-Dialog braucht nur letztere.
     */
    fun getTeams(eventId: UUID, matchId: UUID? = null, registrationId: UUID? = null) = Jooq.query {
        select(
            COMPETITION_MATCH_TEAM.COMPETITION_MATCH.`as`("match_id"),
            COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION,
            COMPETITION_MATCH_TEAM.START_NUMBER,
            COMPETITION_MATCH_TEAM.PLACE,
            COMPETITION_MATCH_TEAM.FAILED,
            COMPETITION_MATCH_TEAM.FAILED_REASON,
            COMPETITION_MATCH_TEAM.PENALTY_SECONDS,
            COMPETITION_MATCH_TEAM.PENALTY_NOTE,
            COMPETITION_REGISTRATION.NAME.`as`("team_name"),
            COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.isNotNull.`as`("deregistered"),
            COMPETITION_DEREGISTRATION.REASON.`as`("deregistration_reason"),
            CLUB.ID.`as`("club_id"),
            CLUB.NAME.`as`("club_name"),
            PARTICIPANT.ID.`as`("participant_id"),
            PARTICIPANT.FIRSTNAME,
            PARTICIPANT.LASTNAME,
            PARTICIPANT.YEAR,
            PARTICIPANT.GENDER,
            PARTICIPANT.EXTERNAL,
            PARTICIPANT.EXTERNAL_CLUB_NAME,
            COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT.`as`("named_participant_id"),
            NAMED_PARTICIPANT.NAME.`as`("named_role"),
            EVENT.MIXED_TEAM_TERM,
            TIMECODE.TIME,
            TIMECODE.BASE_UNIT,
            TIMECODE.MILLISECOND_PRECISION,
            COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.`as`("round_id"),
        )
            .from(COMPETITION_MATCH_TEAM)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .join(COMPETITION_REGISTRATION)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
            .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
            .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
            .leftJoin(NAMED_PARTICIPANT)
            .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
            .leftJoin(COMPETITION_DEREGISTRATION)
            .on(
                COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION)
                    .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            )
            .leftJoin(EVENT_REGISTRATION).on(EVENT_REGISTRATION.ID.eq(COMPETITION_REGISTRATION.EVENT_REGISTRATION))
            .leftJoin(EVENT).on(EVENT_REGISTRATION.EVENT.eq(EVENT.ID))
            .leftJoin(TIMECODE).on(COMPETITION_MATCH_TEAM.TIMECODE.eq(TIMECODE.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH_TEAM.OUT.isTrue.not())
            .and(matchId?.let { COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(it) } ?: DSL.noCondition())
            .and(registrationId?.let { COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(it) } ?: DSL.noCondition())
            .fetch()
    }

    /**
     * Trägt bei allen offenen Mannschaften eines Laufs denselben Ausscheidungsgrund ein. Offen
     * heißt: kein Platz, keine Zeit, nicht bereits ausgeschieden und nicht abgemeldet — für
     * abgemeldete Mannschaften kommt kein Ergebnis mehr.
     *
     * Bereits erfasste Ergebnisse bleiben unangetastet, Plätze werden nicht neu berechnet: die
     * markierten Mannschaften bekommen keinen.
     */
    fun markOpenTeamsFailed(matchId: UUID, reason: String, userId: UUID) = Jooq.query {
        update(COMPETITION_MATCH_TEAM)
            .set(COMPETITION_MATCH_TEAM.FAILED, true)
            .set(COMPETITION_MATCH_TEAM.FAILED_REASON, reason)
            .set(COMPETITION_MATCH_TEAM.UPDATED_BY, userId)
            .set(COMPETITION_MATCH_TEAM.UPDATED_AT, LocalDateTime.now())
            .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(matchId))
            .and(COMPETITION_MATCH_TEAM.OUT.isTrue.not())
            .and(COMPETITION_MATCH_TEAM.FAILED.isTrue.not())
            .and(COMPETITION_MATCH_TEAM.PLACE.isNull)
            .and(COMPETITION_MATCH_TEAM.TIMECODE.isNull)
            .and(
                DSL.notExists(
                    selectOne()
                        .from(COMPETITION_DEREGISTRATION)
                        .where(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
                        .and(
                            COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(
                                DSL.select(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND)
                                    .from(COMPETITION_SETUP_MATCH)
                                    .where(COMPETITION_SETUP_MATCH.ID.eq(matchId))
                            )
                        )
                )
            )
            .execute()
    }

    fun getMatchStartTime(matchId: UUID) = Jooq.query {
        select(COMPETITION_MATCH.START_TIME)
            .from(COMPETITION_MATCH)
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
            .fetchOne(COMPETITION_MATCH.START_TIME)
    }

    /**
     * Läufe, die als nächste anstehen: geplant, noch nicht laufend und noch ohne vollständiges
     * Ergebnis. Sortiert nach Startzeit, damit der Aufrufer die früheste Startzeit greifen kann.
     */
    fun getActivationCandidates(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.START_TIME.isNotNull)
            .and(COMPETITION_MATCH.CURRENTLY_RUNNING.isFalse)
            // Ein beendeter Lauf ist nie wieder Kandidat.
            .and(COMPETITION_MATCH.FINISHED_AT.isNull)
            // mindestens eine Mannschaft ohne Ergebnis: der Lauf steht noch aus. Abgemeldete
            // Mannschaften zählen nicht — auf ihr Ergebnis wartet niemand.
            .and(
                DSL.exists(
                    selectOne()
                        .from(COMPETITION_MATCH_TEAM)
                        .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                        .and(COMPETITION_MATCH_TEAM.OUT.isTrue.not())
                        .and(COMPETITION_MATCH_TEAM.PLACE.isNull)
                        .and(COMPETITION_MATCH_TEAM.FAILED.isTrue.not())
                        .andNotExists(
                            selectOne()
                                .from(COMPETITION_DEREGISTRATION)
                                .where(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
                                .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
                        )
                )
            )
            .orderBy(COMPETITION_MATCH.START_TIME.asc())
            .fetch()
    }

    fun getEventRequirements(eventId: UUID) = Jooq.query {
        select(
            PARTICIPANT_REQUIREMENT.ID,
            PARTICIPANT_REQUIREMENT.NAME,
            PARTICIPANT_REQUIREMENT.DESCRIPTION,
            PARTICIPANT_REQUIREMENT.OPTIONAL,
            PARTICIPANT_REQUIREMENT.CHECK_EARLIEST_MINUTES_BEFORE,
            PARTICIPANT_REQUIREMENT.CHECK_LATEST_MINUTES_BEFORE,
            EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT,
        )
            .from(EVENT_HAS_PARTICIPANT_REQUIREMENT)
            .join(PARTICIPANT_REQUIREMENT)
            .on(EVENT_HAS_PARTICIPANT_REQUIREMENT.PARTICIPANT_REQUIREMENT.eq(PARTICIPANT_REQUIREMENT.ID))
            .where(EVENT_HAS_PARTICIPANT_REQUIREMENT.EVENT.eq(eventId))
            .fetch()
    }

    fun getChecks(eventId: UUID) = Jooq.query {
        with(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT) {
            select(PARTICIPANT, PARTICIPANT_REQUIREMENT, CREATED_AT, NOTE)
                .from(this)
                .where(EVENT.eq(eventId))
                .fetch()
        }
    }

    fun getInvoicePaymentsByClub(eventId: UUID) = Jooq.query {
        with(INVOICE_FOR_EVENT_REGISTRATION) {
            select(CLUB, PAID_AT)
                .from(this)
                .where(EVENT.eq(eventId))
                .fetch()
        }
    }
}
