package de.lambda9.ready2race.backend.app.eventInfo.control

import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CATEGORY
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_DEREGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_SETUP_ROUND
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.TIMECODE
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.DSL.notExists
import org.jooq.impl.DSL.selectOne
import java.util.UUID

/**
 * Die Abfragen hinter dem persönlichen Dashboard "Mein Event".
 *
 * Jede Abfrage schränkt zusätzlich zur Person auf die Veranstaltung ein
 * (`COMPETITION.EVENT.eq(eventId)`). Das ist keine Dopplung der Prüfung im Dienst, sondern die
 * zweite Hälfte davon: der Dienst stellt fest, dass der QR-Code zu dieser Veranstaltung gehört,
 * hier wird sichergestellt, dass auch nur Daten dieser Veranstaltung zurückkommen. Eine Person
 * kann bei mehreren Veranstaltungen starten.
 */
object MyEventRepo {

    /**
     * Alle Läufe, in denen die Person aufgestellt ist - je Lauf eine Zeile pro Mitglied ihrer
     * Mannschaft, damit die Aufstellung ohne zweite Abfrage mitkommt. Der Verbundweg vom Lauf zur
     * Person ist derselbe wie in `CompetitionMatchTeamRepo.getTeamsForUpcomingMatch`:
     * COMPETITION_MATCH -> COMPETITION_MATCH_TEAM -> COMPETITION_REGISTRATION ->
     * COMPETITION_REGISTRATION_NAMED_PARTICIPANT -> PARTICIPANT.
     *
     * Die Auswahl der eigenen Läufe passiert über ein `exists` auf der Meldung und nicht über den
     * Verbund mit PARTICIPANT: sonst fielen die Mitfahrenden aus der Zeilenmenge heraus, und die
     * Aufstellung bestünde nur noch aus der Person selbst.
     */
    fun findMatchesForParticipant(eventId: UUID, participantId: UUID): JIO<List<Record>> = Jooq.query {

        // Wortgleich zu CompetitionMatchRepo.getMatchResults: abgemeldete und ausgeschiedene Boote
        // zählen nicht mit, für sie kommt kein Ergebnis mehr. Ob daraus ein öffentliches Ergebnis
        // wird, entscheidet AthleteBoardLogic.isPublicResult - hier wird nur der Rohwert geliefert.
        val allTeamsScored = notExists(
            selectOne()
                .from(COMPETITION_MATCH_TEAM)
                .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                .and(COMPETITION_MATCH_TEAM.PLACE.isNull)
                .and(COMPETITION_MATCH_TEAM.OUT.isFalse)
                .and(COMPETITION_MATCH_TEAM.FAILED.isFalse)
                .and(
                    notExists(
                        selectOne()
                            .from(COMPETITION_DEREGISTRATION)
                            .where(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
                            .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
                    )
                )
        )

        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.STARTED_AT,
            COMPETITION_MATCH.FINISHED_AT,
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_CATEGORY.NAME.`as`("category_name"),
            COMPETITION_MATCH_TEAM.START_NUMBER,
            COMPETITION_MATCH_TEAM.PLACE,
            COMPETITION_MATCH_TEAM.FAILED,
            COMPETITION_MATCH_TEAM.FAILED_REASON,
            COMPETITION_MATCH_TEAM.PENALTY_SECONDS,
            COMPETITION_MATCH_TEAM.PENALTY_NOTE,
            COMPETITION_REGISTRATION.NAME.`as`("team_name"),
            CLUB.NAME.`as`("club_name"),
            COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.isNotNull.`as`("deregistered"),
            COMPETITION_DEREGISTRATION.REASON.`as`("deregistration_reason"),
            DSL.field(allTeamsScored).`as`("all_teams_scored"),
            PARTICIPANT.ID.`as`("participant_id"),
            PARTICIPANT.FIRSTNAME,
            PARTICIPANT.LASTNAME,
            NAMED_PARTICIPANT.NAME.`as`("named_role"),
            TIMECODE.TIME,
            TIMECODE.BASE_UNIT,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_CATEGORY).on(COMPETITION_CATEGORY.ID.eq(COMPETITION_PROPERTIES.COMPETITION_CATEGORY))
            .join(COMPETITION_MATCH_TEAM)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
            .join(COMPETITION_REGISTRATION)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
            .leftJoin(COMPETITION_DEREGISTRATION)
            .on(
                COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID)
                    .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            )
            .leftJoin(TIMECODE).on(COMPETITION_MATCH_TEAM.TIMECODE.eq(TIMECODE.ID))
            .leftJoin(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
            .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(PARTICIPANT).on(PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT))
            .leftJoin(NAMED_PARTICIPANT)
            .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(ownRegistration(participantId))
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc().nullsLast(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc().nullsLast(),
                PARTICIPANT.LASTNAME.asc().nullsLast(),
            )
            .fetch()
    }

    /**
     * Meldungen der Person, zu denen (noch) kein Lauf gesetzt ist. Bewusst ohne Bezug zur Runde:
     * solange die Mannschaft in gar keinem Lauf steht, ist für die Person offen, wann sie dran ist -
     * und genau das soll die Ansicht sagen können.
     */
    fun findRegistrationsWithoutMatch(eventId: UUID, participantId: UUID): JIO<List<Record>> = Jooq.query {
        select(
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_CATEGORY.NAME.`as`("category_name"),
            COMPETITION_REGISTRATION.NAME.`as`("team_name"),
            NAMED_PARTICIPANT.NAME.`as`("named_role"),
        )
            .from(COMPETITION_REGISTRATION)
            .join(COMPETITION).on(COMPETITION_REGISTRATION.COMPETITION.eq(COMPETITION.ID))
            .join(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_CATEGORY).on(COMPETITION_CATEGORY.ID.eq(COMPETITION_PROPERTIES.COMPETITION_CATEGORY))
            .join(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
            .on(
                COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID)
                    .and(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT.eq(participantId))
            )
            .leftJoin(NAMED_PARTICIPANT)
            .on(NAMED_PARTICIPANT.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.NAMED_PARTICIPANT))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(
                notExists(
                    selectOne()
                        .from(COMPETITION_MATCH_TEAM)
                        .where(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
                )
            )
            .orderBy(COMPETITION_PROPERTIES.IDENTIFIER.asc().nullsLast())
            .fetch()
    }

    /** Name und Verein der Person - der Kopf der Ansicht, den es auch ohne einen einzigen Lauf gibt. */
    fun findParticipant(participantId: UUID): JIO<Record?> = Jooq.query {
        select(
            PARTICIPANT.FIRSTNAME,
            PARTICIPANT.LASTNAME,
            PARTICIPANT.EXTERNAL_CLUB_NAME,
            CLUB.NAME.`as`("club_name"),
        )
            .from(PARTICIPANT)
            .leftJoin(CLUB).on(CLUB.ID.eq(PARTICIPANT.CLUB))
            .where(PARTICIPANT.ID.eq(participantId))
            .fetchOne()
    }

    /**
     * Nur die Kennungen der erfüllten Bedingungen - ausdrücklich nicht die Zeilen selbst. Die
     * Tabelle trägt in `note` eine Freitext-Notiz für interne Augen; was hier nicht geladen wird,
     * kann weiter oben auch nicht versehentlich in die Antwort geraten.
     */
    fun findFulfilledRequirementIds(eventId: UUID, participantId: UUID): JIO<List<UUID>> = Jooq.query {
        select(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT_REQUIREMENT)
            .from(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT)
            .where(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.EVENT.eq(eventId))
            .and(PARTICIPANT_HAS_REQUIREMENT_FOR_EVENT.PARTICIPANT.eq(participantId))
            .fetch { it.value1()!! }
    }

    /** Gehört die gerade betrachtete Meldung zu dieser Person? */
    private fun ownRegistration(participantId: UUID) = DSL.exists(
        selectOne()
            .from(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
            .where(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .and(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT.eq(participantId))
    )
}
