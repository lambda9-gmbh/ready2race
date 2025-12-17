package de.lambda9.ready2race.backend.app.email.entity

import de.lambda9.ready2race.backend.validation.Validatable
import de.lambda9.ready2race.backend.validation.ValidationResult
import de.lambda9.ready2race.backend.validation.emailPattern
import de.lambda9.ready2race.backend.validation.validate
import de.lambda9.ready2race.backend.validation.validators.IntValidators.max
import de.lambda9.ready2race.backend.validation.validators.IntValidators.notNegative
import de.lambda9.ready2race.backend.validation.validators.StringValidators.notBlank
import de.lambda9.ready2race.backend.validation.validators.StringValidators.pattern
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.allOf
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.isValue
import de.lambda9.ready2race.backend.validation.validators.Validator.Companion.oneOf
import org.simplejavamail.api.mailer.config.TransportStrategy

data class SmtpConfigOverrideDto(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val smtpStrategy: TransportStrategy,
    val fromAddress: String,
    val fromName: String?,
    val localhost: String?,
    val replyTo: String?,
) : Validatable {
    override fun validate(): ValidationResult = ValidationResult.allOf(
        this::host validate notBlank,
        this::port validate allOf(notNegative, max(65535)),
        this::username validate notBlank,
        this::password validate notBlank,
        this::smtpStrategy validate oneOf(
            isValue(TransportStrategy.SMTP),
            isValue(TransportStrategy.SMTP_TLS),
            isValue(TransportStrategy.SMTPS)
        ),
        this::fromAddress validate pattern(emailPattern),
        this::fromName validate notBlank,
        this::localhost validate notBlank,
        this::replyTo validate pattern(emailPattern),
    )

    companion object {
        val example
            get() = SmtpConfigOverrideDto(
                host = "host.com",
                port = 187,
                username = "user",
                password = "password",
                smtpStrategy = TransportStrategy.SMTP_TLS,
                fromAddress = "your@mail.com",
                fromName = "no-reply@mail.com (optional)",
                localhost = "localhost (optional)",
                replyTo = "info@mail.com (optional)",
            )
    }
}