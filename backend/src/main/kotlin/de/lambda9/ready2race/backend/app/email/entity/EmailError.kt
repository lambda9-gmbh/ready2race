package de.lambda9.ready2race.backend.app.email.entity

import java.util.*

sealed interface EmailError {
    data class SendingFailed(val emailId: UUID, val cause: Throwable) : EmailError
    data object NoEmailsToSend : EmailError
    data object SmtpConfigMissing : EmailError
}