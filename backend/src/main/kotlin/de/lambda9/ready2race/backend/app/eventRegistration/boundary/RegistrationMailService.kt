package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.email.boundary.EmailService
import de.lambda9.ready2race.backend.app.email.entity.EmailAttachment
import de.lambda9.ready2race.backend.app.email.entity.EmailBody
import de.lambda9.ready2race.backend.app.email.entity.EmailContent
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplatePlaceholder
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventRegistration.control.RegistrationMailRepo
import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationError
import de.lambda9.ready2race.backend.app.eventRegistration.entity.RegistrationMailRecipientDto
import de.lambda9.ready2race.backend.app.eventRegistration.entity.RegistrationMailRequest
import de.lambda9.ready2race.backend.app.eventRegistration.entity.RegistrationMailResultDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit

/**
 * Die Rundmail an alle, die zu einer Veranstaltung gemeldet haben.
 *
 * Bewusst neben [EventRegistrationService] und nicht darin: die Meldelogik dort ist mit über 1200
 * Zeilen schon reichlich, und mit dem Meldevorgang selbst hat das Anschreiben nichts zu tun.
 */
object RegistrationMailService {

    /**
     * Rundmails überleben das Senden um eine Woche, statt wie sonst sofort von
     * `EmailService.deleteSent` geholt zu werden: geht am Regattatag etwas an den falschen Kreis
     * raus, muss am Tag danach noch nachvollziehbar sein, was an wen ging.
     */
    private val keepAfterSending = 7.days

    val KEEP_AFTER_SENDING_SECONDS = keepAfterSending.toLong(DurationUnit.SECONDS)

    fun getRecipients(eventId: UUID): App<Nothing, ApiResponse.ListDto<RegistrationMailRecipientDto>> =
        RegistrationMailRepo.getRecipients(eventId).orDie().map { ApiResponse.ListDto(it) }

    fun send(
        eventId: UUID,
        request: RegistrationMailRequest,
        attachments: List<EmailAttachment>,
        userId: UUID,
    ): App<EventRegistrationError, ApiResponse.Dto<RegistrationMailResultDto>> = KIO.comprehension {

        val event = !EventRepo.get(eventId).orDie().onNullFail { EventRegistrationError.EventNotFound }

        val known = !RegistrationMailRepo.getRecipients(eventId).orDie().map { it.associateBy { r -> r.registrationId } }

        val selected = request.registrationIds.distinct().map { id ->
            val recipient = !KIO.failOnNull(known[id]) { EventRegistrationError.MailRecipientNotFound(id) }
            // Der Dialog bietet Meldungen ohne Nutzer gar nicht erst an. Kommt trotzdem eine
            // herein, ist die Liste veraltet - dann lieber gar nichts verschicken, als eine
            // Rundmail rausgehen zu lassen, in der still jemand fehlt.
            !KIO.failOn(recipient.email == null) { EventRegistrationError.MailRecipientWithoutUser(id) }
            recipient
        }

        val additional = request.additionalAddresses.map { raw ->
            val address = raw.trim()
            !KIO.failOn(!emailPattern.matches(address)) { EventRegistrationError.MailAddressInvalid(raw) }
            address
        }

        // Eine Adresse bekommt genau eine Mail. Steht sie schon als Melder in der Liste, gewinnt
        // der Melder - nur seine Mail kennt Namen und Verein für die Platzhalter.
        val takenAddresses = selected.map { it.email!!.lowercase() }.toMutableSet()
        val extraAddresses = additional.filter { takenAddresses.add(it.lowercase()) }

        !KIO.failOn(selected.isEmpty() && extraAddresses.isEmpty()) { EventRegistrationError.MailWithoutRecipients }

        selected.forEach { recipient ->
            !enqueue(
                address = recipient.email!!,
                request = request,
                eventName = event.name!!,
                recipientName = recipient.name ?: "",
                clubName = recipient.clubName,
                attachments = attachments,
                userId = userId,
            )
        }

        extraAddresses.forEach { address ->
            !enqueue(
                address = address,
                request = request,
                eventName = event.name!!,
                recipientName = "",
                clubName = "",
                attachments = attachments,
                userId = userId,
            )
        }

        KIO.ok(ApiResponse.Dto(RegistrationMailResultDto(enqueued = selected.size + extraAddresses.size)))
    }

    private fun enqueue(
        address: String,
        request: RegistrationMailRequest,
        eventName: String,
        recipientName: String,
        clubName: String,
        attachments: List<EmailAttachment>,
        userId: UUID,
    ): App<Nothing, UUID> {

        fun fill(text: String) = text
            .replace(EmailTemplatePlaceholder.RECIPIENT.key, recipientName)
            .replace(EmailTemplatePlaceholder.CLUB.key, clubName)
            .replace(EmailTemplatePlaceholder.EVENT.key, eventName)

        return EmailService.enqueue(
            recipient = address,
            content = EmailContent(
                subject = fill(request.subject),
                body = EmailBody.Text(fill(request.body)),
            ),
            attachments = attachments,
            keepAfterSending = keepAfterSending,
            appUserId = userId,
        )
    }
}
