package de.lambda9.ready2race.backend.app.email.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.email.entity.EmailContentTemplate
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateDto
import de.lambda9.tailwind.core.KIO

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