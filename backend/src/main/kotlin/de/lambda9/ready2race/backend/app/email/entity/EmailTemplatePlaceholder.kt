package de.lambda9.ready2race.backend.app.email.entity

enum class EmailTemplatePlaceholder(val key: String) {
    RECIPIENT("##recipient##"),
    SENDER("##sender##"),
    LINK("##link##"),
    EVENT("##event##"),
    CLUB("##club##"),
    PARTICIPANTS("##participants##"),
    COMPETITIONS("##competitions##"),
    DATE("##date##"),
}