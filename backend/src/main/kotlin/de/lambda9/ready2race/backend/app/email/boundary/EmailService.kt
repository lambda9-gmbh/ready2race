package de.lambda9.ready2race.backend.app.email.boundary

import de.lambda9.ready2race.backend.*
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.email.control.EmailAttachmentRepo
import de.lambda9.ready2race.backend.app.email.control.EmailRepo
import de.lambda9.ready2race.backend.app.email.entity.EmailAttachment
import de.lambda9.ready2race.backend.app.email.entity.EmailBody
import de.lambda9.ready2race.backend.app.email.entity.EmailContent
import de.lambda9.ready2race.backend.app.email.entity.EmailPriority
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailAttachmentRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailRecord
import de.lambda9.ready2race.backend.kio.accessConfig
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.activation.MimetypesFileTypeMap
import jakarta.mail.util.ByteArrayDataSource
import org.simplejavamail.api.email.AttachmentResource
import org.simplejavamail.email.EmailBuilder
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

object EmailService {

    sealed interface EmailError {
        data class SendingFailed(val emailId: UUID, val cause: Throwable) : EmailError
        data object NoEmailsToSend : EmailError
    }

    private val logger = KotlinLogging.logger {}
    private val retryAfterError = 5.minutes

    fun enqueue(
        recipient: String,
        content: EmailContent,
        attachments: List<EmailAttachment> = emptyList(),
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        priority: EmailPriority = EmailPriority.NORMAL,
        dontSendBefore: LocalDateTime = LocalDateTime.now(),
        keepAfterSending: Duration = Duration.ZERO,
        appUserId: UUID = SYSTEM_USER,
    ): App<Nothing, UUID> = App.comprehension {

        val now = LocalDateTime.now()
        val (body, bodyIsHtml) = when (val body = content.body) {
            is EmailBody.Html -> body.html to true
            is EmailBody.Text -> body.text to false
        }

        val id = !EmailRepo.create(
            EmailRecord(
                id = UUID.randomUUID(),
                recipient = recipient,
                subject = content.subject,
                body = body,
                bodyIsHtml = bodyIsHtml,
                cc = cc.takeIf { it.isNotEmpty() }?.joinToString(";"),
                bcc = bcc.takeIf { it.isNotEmpty() }?.joinToString(";"),
                priority = priority.value,
                dontSendBefore = dontSendBefore,
                keepAfterSending = keepAfterSending.toLong(DurationUnit.SECONDS),
                createdAt = now,
                createdBy = appUserId,
                updatedAt = now,
                updatedBy = appUserId,
            )
        ).orDie()

        !EmailAttachmentRepo.create(
            attachments.map {
                EmailAttachmentRecord(
                    email = id,
                    name = it.name,
                    data = it.data
                )
            }
        ).orDie()

        KIO.ok(id)
    }

    fun sendNext(): App<EmailError, Unit> = KIO.comprehension {

        val smtp = (!accessConfig()).smtp

        val email = !EmailRepo.getAndLockNext(retryAfterError).orDie().onNullFail { EmailError.NoEmailsToSend }
        val attachments = !EmailAttachmentRepo.getByEmail(email.id!!).orDie().map { records ->
            records.map {
                AttachmentResource(
                    it.name!!,
                    ByteArrayDataSource(
                        it.data!!,
                        MimetypesFileTypeMap().getContentType(it.name!!)
                    )
                )
            }
        }

        !KIO.effect {
            smtp.createMailer().sendMail(
                EmailBuilder.startingBlank()
                    .from(smtp.from.name, smtp.from.address)
                    .to(email.recipient!!)
                    .applyNotNull(email.cc) { cc(it) }
                    .applyNotNull(email.bcc) { bcc(it) }
                    .withSubject(email.subject)
                    .applyEither(
                        email.bodyIsHtml!!,
                        { withHTMLText(email.body) },
                        { withPlainText(email.body) }
                    )
                    .applyNotNull(smtp.replyTo) { withReplyTo(it) }
                    .withAttachments(attachments)
                    .buildEmail()
            )
        }.mapError {
            val now = LocalDateTime.now()
            email.lastError = it.stackTraceToString()
            email.lastErrorAt = now
            email.updatedAt = now
            email.updatedBy = SYSTEM_USER
            email.update()
            EmailError.SendingFailed(email.id!!, it)
        }

        val now = LocalDateTime.now()
        email.sentAt = now
        email.updatedAt = now
        email.updatedBy = SYSTEM_USER
        email.update()

        KIO.unit
    }

    fun deleteSent(): App<Nothing, Int> =
        EmailRepo.deleteSent().orDie()

}