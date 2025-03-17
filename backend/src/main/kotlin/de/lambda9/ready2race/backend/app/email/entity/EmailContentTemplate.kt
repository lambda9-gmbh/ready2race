package de.lambda9.ready2race.backend.app.email.entity

import de.lambda9.ready2race.backend.database.generated.tables.records.EmailIndividualTemplateRecord

sealed interface EmailContentTemplate {

    data class Default(
        val subject: String,
        val body: String,
    ) : EmailContentTemplate

    @JvmInline
    value class Individual(val template: EmailIndividualTemplateRecord) : EmailContentTemplate

    fun toContent(
        vararg replacements: Pair<EmailTemplatePlaceholder, String>,
    ): EmailContent {

        fun replacePlaceholders(template: String) =
            replacements.fold(template) { string, (placeholder, value) -> string.replace(placeholder.key, value) }

        return when (this) {
            is Default ->
                EmailContent(
                    subject = replacePlaceholders(subject),
                    body = EmailBody.Text(replacePlaceholders(body))
            )

            is Individual ->
                EmailContent(
                    subject = replacePlaceholders(template.subject),
                    body = replacePlaceholders(template.body).let {
                        if (template.bodyIsHtml) {
                            EmailBody.Html(it)
                        } else {
                            EmailBody.Text(it)
                        }
                    }
                )

        }
    }
}