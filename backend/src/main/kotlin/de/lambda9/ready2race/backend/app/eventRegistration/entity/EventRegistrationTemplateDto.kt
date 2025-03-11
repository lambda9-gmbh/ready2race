package de.lambda9.ready2race.backend.app.eventRegistration.entity

data class EventRegistrationTemplateDto(
    val info: EventRegistrationInfoDto,
    val upsertableRegistration: EventRegistrationUpsertDto
)

