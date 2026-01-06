package de.lambda9.ready2race.backend.app.email.entity

data class EmailPlaceholdersDto(
    val key: EmailTemplateKey,
    val required: List<String> = emptyList(),
    val optional: List<String> = emptyList(),
)

val emailPlaceholderMapping: Map<EmailTemplateKey, EmailPlaceholders> = mapOf(
    EmailTemplateKey.USER_REGISTRATION to EmailPlaceholders(
        required = listOf(EmailTemplatePlaceholder.LINK.name),
        optional = listOf(EmailTemplatePlaceholder.RECIPIENT.name)
    ),
    EmailTemplateKey.USER_INVITATION to EmailPlaceholders(
        required = listOf(EmailTemplatePlaceholder.LINK.name),
        optional = listOf(EmailTemplatePlaceholder.SENDER.name, EmailTemplatePlaceholder.RECIPIENT.name )
    ),
    EmailTemplateKey.USER_RESET_PASSWORD to EmailPlaceholders(
        required = listOf(EmailTemplatePlaceholder.LINK.name),
        optional = listOf(EmailTemplatePlaceholder.RECIPIENT.name)
    ),
    EmailTemplateKey.EVENT_REGISTRATION_CONFIRMATION to EmailPlaceholders(
        required = listOf(EmailTemplatePlaceholder.RECIPIENT.name,
            EmailTemplatePlaceholder.EVENT.name,
            EmailTemplatePlaceholder.CLUB.name,
            EmailTemplatePlaceholder.PARTICIPANTS.name,
            EmailTemplatePlaceholder.COMPETITIONS.name)
    ),
    EmailTemplateKey.EVENT_REGISTRATION_INVOICE to EmailPlaceholders(
        required = listOf(EmailTemplatePlaceholder.EVENT.name,
            EmailTemplatePlaceholder.RECIPIENT.name,
            EmailTemplatePlaceholder.DATE.name)
    ),
    EmailTemplateKey.PARTICIPANT_CHALLENGE_REGISTERED to EmailPlaceholders(
        required = listOf(EmailTemplatePlaceholder.LINK.name),
        optional = listOf(EmailTemplatePlaceholder.RECIPIENT.name,
            EmailTemplatePlaceholder.EVENT.name)
    ),
    EmailTemplateKey.CERTIFICATE_OF_PARTICIPATION_PARTICIPANT to EmailPlaceholders(
        required = listOf(EmailTemplatePlaceholder.RECIPIENT.name,
            EmailTemplatePlaceholder.EVENT.name)
    )
)
