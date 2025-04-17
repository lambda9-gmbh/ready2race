package de.lambda9.ready2race.backend.app.eventRegistration.entity

import java.time.LocalDate
import java.util.*

data class EventRegistrationDayDto(
    val id: UUID,
    val date: LocalDate,
    val name: String?,
    val description: String?
)