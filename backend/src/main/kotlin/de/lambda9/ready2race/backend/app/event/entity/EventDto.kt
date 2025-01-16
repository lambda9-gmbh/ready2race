package de.lambda9.ready2race.backend.app.event.entity

import java.util.*

data class EventDto(
    val id: UUID,
    val properties: EventProperties
)