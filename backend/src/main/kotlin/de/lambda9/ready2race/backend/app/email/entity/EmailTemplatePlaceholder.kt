package de.lambda9.ready2race.backend.app.email.entity

enum class EmailTemplatePlaceholder(val key: String) {
    RECIPIENT("##recipient##"),
    LINK("##link##")
}