package de.lambda9.ready2race.backend.app.email.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.email.entity.EmailContentTemplate
import de.lambda9.ready2race.backend.app.email.entity.AssignedEmailDto
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateDto
import de.lambda9.ready2race.backend.app.email.entity.SmtpConfigOverrideDto
import de.lambda9.ready2race.backend.config.Config
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.SmtpConfigOverrideRecord
import de.lambda9.tailwind.core.KIO
import org.simplejavamail.api.mailer.config.TransportStrategy

fun EmailContentTemplate.toDto(): App<Nothing, EmailTemplateDto> = KIO.ok(
    when (this) {
        is EmailContentTemplate.Default ->
            EmailTemplateDto(
                subject = subject,
                body = body,
                bodyIsHtml = false,
            )

        is EmailContentTemplate.Individual ->
            EmailTemplateDto(
                subject = template.subject,
                body = template.body,
                bodyIsHtml = template.bodyIsHtml,
            )
    }
)

fun EmailRecord.toAssignedDto(): App<Nothing, AssignedEmailDto> =
    KIO.ok(
        AssignedEmailDto(
            recipient = recipient,
            sentAt = sentAt,
            lastErrorAt = lastErrorAt,
            lastError = lastError,
        )
    )

fun SmtpConfigOverrideRecord.toDto(): App<Nothing, SmtpConfigOverrideDto> = KIO.ok(
    SmtpConfigOverrideDto(
        host = host,
        port = port,
        username = username,
        password = "",
        smtpStrategy = TransportStrategy.valueOf(smtpStrategy),
        fromAddress = fromAddress,
        fromName = fromName,
        localhost = smtpLocalhost,
        replyTo = replyTo,
    )
)

fun SmtpConfigOverrideRecord.toSmtpConfig(): App<Nothing, Config.Smtp> = KIO.ok(
    Config.Smtp(
        host = host,
        port = port,
        user = username,
        password = password,
        strategy = TransportStrategy.valueOf(smtpStrategy,),
        from = Config.Smtp.From(
            address = fromAddress,
            name = fromName
        ),
        replyTo = replyTo,
        localhost = smtpLocalhost
    )
)

fun Config.Smtp.toDto(): App<Nothing, SmtpConfigOverrideDto> = KIO.ok(
    SmtpConfigOverrideDto(
        host = host,
        port = port,
        username = user,
        password = "",
        smtpStrategy = strategy,
        fromAddress = from.address,
        fromName = from.name,
        localhost = localhost,
        replyTo = replyTo,
    )
)

