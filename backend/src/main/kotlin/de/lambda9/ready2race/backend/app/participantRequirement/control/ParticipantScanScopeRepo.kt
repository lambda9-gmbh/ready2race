package de.lambda9.ready2race.backend.app.participantRequirement.control

import de.lambda9.ready2race.backend.app.participantRequirement.entity.ParticipantScanCompetitionDto
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_REGISTRATION_NAMED_PARTICIPANT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object ParticipantScanScopeRepo {

    /**
     * Die Wettkämpfe, in denen eine Person bei dieser Veranstaltung gemeldet ist.
     *
     * Gleiche Verbindungskette wie in [OpenRequirementExportRepo.getCompetitionsByParticipant],
     * nur für eine Person und mit den Kennungen statt eines fertigen Textes: die Scan-App baut
     * daraus eine Auswahl und muss die Wettkampf-Kennung zurückschicken können.
     *
     * `distinct`, weil eine Person in einem Wettkampf in mehreren Rollen gemeldet sein kann
     * (Steuermann und Ruderin in derselben Meldung) - für die Waage ist das ein Wettkampf.
     */
    fun getCompetitionsOfParticipant(
        eventId: UUID,
        participantId: UUID,
    ): JIO<List<ParticipantScanCompetitionDto>> = Jooq.query {
        selectDistinct(
            COMPETITION.ID,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.NAME,
            COMPETITION_PROPERTIES.SHORT_NAME,
        )
            .from(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
            .join(COMPETITION_REGISTRATION)
            .on(COMPETITION_REGISTRATION.ID.eq(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION))
            .join(COMPETITION)
            .on(COMPETITION.ID.eq(COMPETITION_REGISTRATION.COMPETITION))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(
                COMPETITION.EVENT.eq(eventId)
                    .and(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT.eq(participantId))
            )
            .orderBy(COMPETITION_PROPERTIES.IDENTIFIER)
            .fetch { r ->
                ParticipantScanCompetitionDto(
                    id = r[COMPETITION.ID]!!,
                    identifier = r[COMPETITION_PROPERTIES.IDENTIFIER],
                    name = r[COMPETITION_PROPERTIES.NAME]!!,
                    shortName = r[COMPETITION_PROPERTIES.SHORT_NAME],
                )
            }
    }

    /**
     * Alle Wettkämpfe der Veranstaltung - die Auswahl für den Abgleich im Verwaltungs-UI, der
     * anders als der Scan an der Waage nicht von einer Person ausgeht, sondern von der Bedingung.
     */
    fun getCompetitionsOfEvent(eventId: UUID): JIO<List<ParticipantScanCompetitionDto>> = Jooq.query {
        select(
            COMPETITION.ID,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.NAME,
            COMPETITION_PROPERTIES.SHORT_NAME,
        )
            .from(COMPETITION)
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .orderBy(COMPETITION_PROPERTIES.IDENTIFIER)
            .fetch { r ->
                ParticipantScanCompetitionDto(
                    id = r[COMPETITION.ID]!!,
                    identifier = r[COMPETITION_PROPERTIES.IDENTIFIER],
                    name = r[COMPETITION_PROPERTIES.NAME]!!,
                    shortName = r[COMPETITION_PROPERTIES.SHORT_NAME],
                )
            }
    }
}
