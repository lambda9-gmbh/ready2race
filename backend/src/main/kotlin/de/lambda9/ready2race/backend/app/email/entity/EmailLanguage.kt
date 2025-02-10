package de.lambda9.ready2race.backend.app.email.entity

import com.fasterxml.jackson.annotation.JsonValue

enum class EmailLanguage(@JsonValue val value: String) {
    EN("en"),
    DE("de"),
}