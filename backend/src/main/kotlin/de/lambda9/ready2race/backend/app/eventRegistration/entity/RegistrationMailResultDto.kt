package de.lambda9.ready2race.backend.app.eventRegistration.entity

/**
 * [enqueued] ist die Zahl der tatsächlich eingereihten Mails - nach dem Zusammenfallen von
 * Adressen also nicht zwangsläufig so viele, wie der Dialog abgeschickt hat.
 */
data class RegistrationMailResultDto(
    val enqueued: Int,
)
