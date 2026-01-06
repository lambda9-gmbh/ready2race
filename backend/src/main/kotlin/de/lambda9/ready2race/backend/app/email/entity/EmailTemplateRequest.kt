package de.lambda9.ready2race.backend.app.email.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank

data class EmailTemplateRequest(
    val subject: String,
    val body: String,
    val bodyIsHtml: Boolean,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::subject validate notBlank,
        this::body validate notBlank,
    )
    companion object {
        val example
            get() = EmailTemplateRequest(
                subject = "Email Subject",
                body = "Email Body",
                bodyIsHtml = false,
            )
    }
}