package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.participantRequirement.boundary.OpenRequirementLogic.RequirementScope
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_HAS_PARTICIPANT_REQUIREMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.NAMED_PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT_REQUIREMENT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

/**
 * Die Abfragen, die der Export der offenen Bedingungen zusätzlich zum View
 * `participant_for_event` braucht: die Geltung der Bedingungen, die Rollennamen, die Rennen
 * je gemeldeter Person und die E-Mail des Meldenden je Verein.
 */
object OpenRequirementExportRepo {

    /**
     * Die an der Veranstaltung aktiven Bedingungen mit ihrer Geltung - eine Zeile je Zuordnung,
     * also mehrfach, wenn eine Bedingung an mehreren Rollen hängt. Sortiert nach Namen, damit die
     * Spalte "Fehlende Bedingungen" über Exporte hinweg dieselbe Reihenfolge hat.
     */
    fun getActiveRequirementScopes(eventId: UUID): JIO<List<RequirementScope>> = Jooq.query {
        select(
            PARTICIPANT_REQUIREMENT.ID,
            PARTICIPANT_REQUIREMENT.NAME,
            EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT,
        )
            .from(EVENT_HAS_PARTICIPANT_REQUIREMENT)
            .join(PARTICIPANT_REQUIREMENT)
            .on(PARTICIPANT_REQUIREMENT.ID.eq(EVENT_HAS_PARTICIPANT_REQUIREMENT.PARTICIPANT_REQUIREMENT))
            .where(EVENT_HAS_PARTICIPANT_REQUIREMENT.EVENT.eq(eventId))
            .orderBy(PARTICIPANT_REQUIREMENT.NAME)
            .fetch { r ->
                RequirementScope(
                    id = r[PARTICIPANT_REQUIREMENT.ID]!!,
                    name = r[PARTICIPANT_REQUIREMENT.NAME]!!,
                    namedParticipantId = r[EVENT_HAS_PARTICIPANT_REQUIREMENT.NAMED_PARTICIPANT],
                )
            }
    }

    /** Rollennamen, um die UUIDs aus `named_participant_ids` beschriften zu können. */
    fun getNamedParticipantNames(): JIO<Map<UUID, String>> = Jooq.query {
        select(NAMED_PARTICIPANT.ID, NAMED_PARTICIPANT.NAME)
            .from(NAMED_PARTICIPANT)
            .fetch { it[NAMED_PARTICIPANT.ID]!! to it[NAMED_PARTICIPANT.NAME]!! }
            .toMap()
    }

    /**
     * Die Rennen je gemeldeter Person, als "Rennnummer Kurzbezeichnung" - dieselbe Schreibweise
     * wie im Zeitplan, damit sich die Zeile ohne Nachschlagen zuordnen lässt.
     */
    fun getCompetitionsByParticipant(eventId: UUID): JIO<Map<UUID, List<String>>> = Jooq.query {
        select(
            COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.SHORT_NAME,
            COMPETITION_PROPERTIES.NAME,
        )
            .from(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
            .join(COMPETITION_REGISTRATION)
            .on(COMPETITION_REGISTRATION.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION))
            .join(COMPETITION)
            .on(COMPETITION.ID.eq(COMPETITION_REGISTRATION.COMPETITION))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .orderBy(COMPETITION_PROPERTIES.IDENTIFIER)
            .fetch { r ->
                val label = listOfNotNull(
                    r[COMPETITION_PROPERTIES.IDENTIFIER],
                    r[COMPETITION_PROPERTIES.SHORT_NAME] ?: r[COMPETITION_PROPERTIES.NAME],
                ).joinToString(" ")
                r[COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT]!! to label
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, labels) -> labels.distinct() }
    }

    /**
     * Je Verein die E-Mail der Person, die die Meldung zur Veranstaltung abgegeben hat - der
     * `created_by`-User der `event_registration`. Die Athleten selbst haben oft keine eigene
     * Adresse hinterlegt; der Meldende ist dann der einzige erreichbare Kontakt.
     *
     * Vereine ohne Meldung und Meldungen mit gelöschtem Ersteller (`created_by` steht dann per
     * `on delete set null` auf NULL) fehlen bewusst in der Map - der innere Join lässt sie heraus,
     * die Spalte bleibt für sie leer.
     */
    fun getRegistrantEmailByClub(eventId: UUID): JIO<Map<UUID, String>> = Jooq.query {
        select(EVENT_REGISTRATION.CLUB, APP_USER.EMAIL)
            .from(EVENT_REGISTRATION)
            .join(APP_USER)
            .on(APP_USER.ID.eq(EVENT_REGISTRATION.CREATED_BY))
            .where(EVENT_REGISTRATION.EVENT.eq(eventId))
            .fetch { r -> r[EVENT_REGISTRATION.CLUB] to r[APP_USER.EMAIL] }
            .mapNotNull { (club, email) -> if (club != null && email != null) club to email else null }
            .toMap()
    }
}
