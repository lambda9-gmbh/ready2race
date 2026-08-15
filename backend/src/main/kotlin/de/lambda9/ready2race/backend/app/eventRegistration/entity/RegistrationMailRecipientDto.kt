package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.util.UUID

/**
 * Ein Melder der Veranstaltung, so wie ihn der Rundmail-Dialog zur Auswahl anbietet.
 *
 * [name] und [email] sind null, wenn der Nutzer, der die Meldung abgeschickt hat, inzwischen
 * gelöscht ist (`event_registration.created_by` steht per `on delete set null` auf NULL). Die
 * Meldung verschwindet dadurch nicht - der Verein muss in der Liste sichtbar bleiben, sonst
 * bemerkt niemand, dass er die Rundmail nicht bekommt.
 */
data class RegistrationMailRecipientDto(
    val registrationId: UUID,
    val clubId: UUID,
    val clubName: String,
    val name: String?,
    val email: String?,
)
