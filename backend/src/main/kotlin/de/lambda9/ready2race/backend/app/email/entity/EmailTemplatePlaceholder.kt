package de.lambda9.ready2race.backend.app.email.entity

enum class EmailTemplatePlaceholder {
    RECIPIENT,
    SENDER,
    LINK,
    EVENT,
    CLUB,
    PARTICIPANTS,
    COMPETITIONS,
    DATE;

    val key get() = "##${name.lowercase()}##"
}