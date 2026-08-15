package de.lambda9.ready2race.backend.app.eventRegistration.control

import de.lambda9.ready2race.backend.app.eventRegistration.entity.RegistrationMailRecipientDto
import de.lambda9.ready2race.backend.database.generated.tables.references.APP_USER
import de.lambda9.ready2race.backend.database.generated.tables.references.CLUB
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_REGISTRATION
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object RegistrationMailRepo {

    /**
     * Die Melder einer Veranstaltung: je Meldung der Verein und die Person, die sie abgeschickt
     * hat.
     *
     * Der Weg zur Person führt über `event_registration.created_by` und nicht über den Verein -
     * ein Verein kann Nutzer haben, die zu dieser Veranstaltung nichts gemeldet haben, und meldet
     * zu mehreren Veranstaltungen. Der Join auf app_user ist deshalb ein LEFT JOIN: gelöschte
     * Ersteller lassen die Meldung stehen und nur die Adresse fehlen.
     */
    fun getRecipients(eventId: UUID): JIO<List<RegistrationMailRecipientDto>> = Jooq.query {
        select(
            EVENT_REGISTRATION.ID,
            CLUB.ID,
            CLUB.NAME,
            APP_USER.FIRSTNAME,
            APP_USER.LASTNAME,
            APP_USER.EMAIL,
        )
            .from(EVENT_REGISTRATION)
            .join(CLUB).on(CLUB.ID.eq(EVENT_REGISTRATION.CLUB))
            .leftJoin(APP_USER).on(APP_USER.ID.eq(EVENT_REGISTRATION.CREATED_BY))
            .where(EVENT_REGISTRATION.EVENT.eq(eventId))
            .orderBy(CLUB.NAME.asc())
            .fetch { record ->
                val email = record[APP_USER.EMAIL]
                RegistrationMailRecipientDto(
                    registrationId = record[EVENT_REGISTRATION.ID]!!,
                    clubId = record[CLUB.ID]!!,
                    clubName = record[CLUB.NAME]!!,
                    // Name und Adresse hängen am selben Nutzer: fehlt er, fehlen beide.
                    name = email?.let { "${record[APP_USER.FIRSTNAME]} ${record[APP_USER.LASTNAME]}" },
                    email = email,
                )
            }
    }
}
