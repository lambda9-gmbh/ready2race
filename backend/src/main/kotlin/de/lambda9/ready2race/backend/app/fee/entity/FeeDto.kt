package de.lambda9.ready2race.backend.app.fee.entity

import java.util.*

data class FeeDto(
    val id: UUID,
    val name: String,
    val description: String?
)