package de.lambda9.ready2race.backend.app.email.entity

enum class EmailPriority(val value: Int) {
    LOW(-1),
    NORMAL(0),
    HIGH(1)
}