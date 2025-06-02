package de.lambda9.ready2race.backend.app.contactInformation.entity

import java.util.UUID

data class ContactInformationDto(
    val id: UUID,
    val name: String,
    val addressZip: String,
    val addressCity: String,
    val addressStreet: String,
    val email: String,
)
