package de.lambda9.ready2race.backend.app.email.boundary

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appuser.boundary.AppUserService.fullName
import de.lambda9.ready2race.backend.app.email.control.EmailAttachmentRepo
import de.lambda9.ready2race.backend.app.email.control.EmailIndividualTemplateRepo
import de.lambda9.ready2race.backend.app.email.control.EmailRepo
import de.lambda9.ready2race.backend.app.email.control.toDto
import de.lambda9.ready2race.backend.app.email.control.toSmtpConfig
import de.lambda9.ready2race.backend.app.email.entity.*
import de.lambda9.ready2race.backend.app.email.entity.SmtpConfigOverrideDto
import de.lambda9.ready2race.backend.applyEither
import de.lambda9.ready2race.backend.applyNotNull
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailAttachmentRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailIndividualTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SmtpConfigOverrideRecord
import de.lambda9.ready2race.backend.kio.accessConfig
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.andThenNotNull
import de.lambda9.tailwind.core.extensions.kio.mapNotNull
import de.lambda9.tailwind.core.extensions.kio.onNull
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.activation.MimetypesFileTypeMap
import jakarta.mail.util.ByteArrayDataSource
import org.simplejavamail.api.email.AttachmentResource
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

object EmailService {

    private data class DefaultEmailTemplatesFromFile(
        val footer: List<String>,
        val templates: List<DefaultEmailTemplate>,
    ) {

        data class DefaultEmailTemplate(
            val key: EmailTemplateKey,
            val subject: String,
            val body: List<String>,
        )
    }

    private val logger = KotlinLogging.logger {}
    private val retryAfterError = 5.minutes
    private val defaultTemplates =
        EmailLanguage.entries.flatMap { lng ->
            val lngFile = "${lng}.json"
            logger.info { "Reading email language file $lngFile" }
            val resource = javaClass.classLoader
                .getResourceAsStream("internationalization/email/templates/$lngFile")
                ?.bufferedReader()
                ?: throw Exception("Cannot find email internationalization resource: $lngFile")

            val fromFile = jsonMapper.readValue<DefaultEmailTemplatesFromFile>(resource)

            fromFile.templates.groupingBy { it.key }.eachCount().filter { it.value > 1 }.takeIf { it.isNotEmpty() }
                ?.let {
                    throw Exception("Following keys in $lngFile are declared multiple times: ${it.keys.joinToString(", ")}")
                }

            EmailTemplateKey.entries.map { key ->
                fromFile.templates.find { it.key == key }?.let {
                    val body = it.body.joinToString("\n") + "\n\n" + fromFile.footer.joinToString("\n")
                    (it.key to lng) to EmailContentTemplate.Default(it.subject, body, it.key, lng)
                } ?: throw Exception("Missing key in $lngFile: $key")
            }
        }.toMap()

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

        val smtpOverride = !EmailRepo.getSMTPConfigOverride().andThenNotNull {
            it.toSmtpConfig()
        }.orDie()
        val config = !accessConfig()
        !KIO.failOn(config.smtp == null && smtpOverride == null) {
            EmailError.SmtpConfigMissing
        }
        val smtp = smtpOverride ?: config.smtp!!

        val email = !EmailRepo.getAndLockNext(retryAfterError).orDie().onNullFail { EmailError.NoEmailsToSend }
        val attachments = !EmailAttachmentRepo.getByEmail(email.id).orDie().map { records ->
            records.map {
                AttachmentResource(
                    it.name,
                    ByteArrayDataSource(
                        it.data,
                        MimetypesFileTypeMap().getContentType(it.name)
                    )
                )
            }
        }

        if (config.mailReceiverWhitelist == null || config.mailReceiverWhitelist.any { email.recipient.matches(it) }) {
            !KIO.effect {
                smtp.createMailer().sendMail(
                    EmailBuilder.startingBlank()
                        .from(smtp.from.name, smtp.from.address)
                        .to(email.recipient)
                        .applyNotNull(email.cc) { cc(it) }
                        .applyNotNull(email.bcc) { bcc(it) }
                        .withSubject(email.subject)
                        .applyEither(
                            email.bodyIsHtml,
                            { withHTMLText(email.body) },
                            { withPlainText(email.body) }
                        )
                        .applyNotNull(smtp.replyTo) { withReplyTo(it) }
                        .withAttachments(attachments)
                        .buildEmail()
                )
            }.mapError {
                val now = LocalDateTime.now()
                !EmailRepo.update(email) {
                    lastError = it.stackTraceToString()
                    lastErrorAt = now
                    updatedAt = now
                    updatedBy = SYSTEM_USER
                }.orDie()
                EmailError.SendingFailed(email.id, it)
            }
        } else {
            logger.info { "EMAIL: Receiver not in whitelist; skipping email sending..." }
        }

        val now = LocalDateTime.now()
        !EmailRepo.update(email) {
            sentAt = now
            updatedAt = now
            updatedBy = SYSTEM_USER
        }.orDie()

        KIO.unit
    }

    fun deleteSent(): App<Nothing, Int> =
        EmailRepo.deleteSent().orDie()


    fun getTemplate(
        key: EmailTemplateKey,
        language: EmailLanguage,
    ): App<Nothing, EmailContentTemplate> =
        EmailIndividualTemplateRepo.get(key, language).orDie()
            .map {
                it?.let { EmailContentTemplate.Individual(it) }
                    ?: defaultTemplates[key to language]!!
            }

    fun getTemplates(
        language: EmailLanguage,
    ): App<Nothing, ApiResponse.ListDto<EmailTemplateDto>> = KIO.comprehension {
        val individuals = !EmailIndividualTemplateRepo.getAll(language).orDie()
            .map {
                it.map { EmailContentTemplate.Individual(it).toDto() }
            }
        val defaults = EmailTemplateKey.entries.mapNotNull { key ->
            if (individuals.any {it.key == key}){
                null
            } else {
                defaultTemplates[key to language]!!.toDto()
            }
        }
        val list = (individuals + defaults).sortedBy { it.key.name }
        KIO.ok(ApiResponse.ListDto(list))
    }

    fun deleteTemplate(
        key: EmailTemplateKey,
        language: EmailLanguage,
    ): App<Nothing, ApiResponse.NoData> =
        EmailIndividualTemplateRepo.delete(key, language).orDie().map { ApiResponse.NoData }

    fun setTemplate(
        key: EmailTemplateKey,
        language: EmailLanguage,
        userId: UUID,
        template: EmailTemplateRequest
    ): App<Nothing, ApiResponse.NoData> =
        EmailIndividualTemplateRepo.replaceIndividualTemplate(
            key,
            language,
            EmailIndividualTemplateRecord(
                key = key.name,
                language = language.name,
                subject = template.subject,
                body = template.body,
                bodyIsHtml = template.bodyIsHtml,
                createdAt = LocalDateTime.now(),
                createdBy = userId,
                updatedAt = LocalDateTime.now(),
                updatedBy = userId,
            )
        ).orDie().map { ApiResponse.NoData }

    fun getSMTPConfigOverride(): App<Nothing, ApiResponse.Dto<SmtpConfigOverrideDto>> =
        EmailRepo.getSMTPConfigOverride().orDie()
            .andThenNotNull { it.toDto() }
            .onNull { accessConfig().map { it.smtp!! }.andThen { it.toDto() } }
            .map { ApiResponse.Dto(it) }

    fun setSMTPConfigOverride(override: SmtpConfigOverrideDto, userId: UUID): App<Nothing, ApiResponse.NoData> =
        EmailRepo.replaceSMTPConfigOverride(
            SmtpConfigOverrideRecord(
                id = UUID.randomUUID(),
                host = override.host,
                port = override.port,
                username = override.username,
                password = override.password,
                smtpStrategy = override.smtpStrategy.name.uppercase(),
                fromAddress = override.fromAddress,
                fromName = override.fromName,
                smtpLocalhost = override.localhost,
                replyTo = override.replyTo,
                createdAt = LocalDateTime.now(),
                createdBy = userId,
            )
        ).orDie().map { ApiResponse.NoData }

    fun deleteSMTPConfigOverride(): App<Nothing, ApiResponse.NoData> =
        EmailRepo.deleteSMTPConfigOverride().orDie().map { ApiResponse.NoData }

    val emailPlaceholderMapping: Map<EmailTemplateKey, EmailPlaceholders> = mapOf(
        EmailTemplateKey.USER_REGISTRATION to EmailPlaceholders(
            required = listOf(EmailTemplatePlaceholder.LINK.name.lowercase()),
            optional = listOf(EmailTemplatePlaceholder.RECIPIENT.name.lowercase())
        ),
        EmailTemplateKey.USER_INVITATION to EmailPlaceholders(
            required = listOf(EmailTemplatePlaceholder.LINK.name.lowercase()),
            optional = listOf(EmailTemplatePlaceholder.SENDER.name.lowercase(),
                EmailTemplatePlaceholder.RECIPIENT.name.lowercase() )
        ),
        EmailTemplateKey.USER_RESET_PASSWORD to EmailPlaceholders(
            required = listOf(EmailTemplatePlaceholder.LINK.name.lowercase()),
            optional = listOf(EmailTemplatePlaceholder.RECIPIENT.name.lowercase())
        ),
        EmailTemplateKey.EVENT_REGISTRATION_CONFIRMATION to EmailPlaceholders(
            required = listOf(EmailTemplatePlaceholder.RECIPIENT.name.lowercase(),
                EmailTemplatePlaceholder.EVENT.name.lowercase(),
                EmailTemplatePlaceholder.CLUB.name.lowercase(),
                EmailTemplatePlaceholder.PARTICIPANTS.name.lowercase(),
                EmailTemplatePlaceholder.COMPETITIONS.name.lowercase())
        ),
        EmailTemplateKey.EVENT_REGISTRATION_INVOICE to EmailPlaceholders(
            required = listOf(EmailTemplatePlaceholder.EVENT.name.lowercase(),
                EmailTemplatePlaceholder.RECIPIENT.name.lowercase(),
                EmailTemplatePlaceholder.DATE.name.lowercase())
        ),
        EmailTemplateKey.PARTICIPANT_CHALLENGE_REGISTERED to EmailPlaceholders(
            required = listOf(EmailTemplatePlaceholder.LINK.name.lowercase()),
            optional = listOf(EmailTemplatePlaceholder.RECIPIENT.name.lowercase(),
                EmailTemplatePlaceholder.EVENT.name.lowercase())
        ),
        EmailTemplateKey.CERTIFICATE_OF_PARTICIPATION_PARTICIPANT to EmailPlaceholders(
            optional = listOf(EmailTemplatePlaceholder.RECIPIENT.name.lowercase(),
                EmailTemplatePlaceholder.EVENT.name.lowercase())
        )
    )

}