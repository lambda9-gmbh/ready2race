package de.lambda9.ready2race.backend.app.appuser.entity

import java.util.*

data class AppUserForEventDto(
    val id: UUID,
    val firstname: String,
    val lastname: String,
    val email: String,
    val club: UUID?,
    val qrCodeId: String?,
)