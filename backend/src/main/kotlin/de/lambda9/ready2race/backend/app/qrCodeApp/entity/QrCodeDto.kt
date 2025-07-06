package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import java.util.*

sealed class QrCodeDto() {
    data class QrCodeParticipantResponseDto(
        val firstname: String,
        val lastname: String,
        val id: UUID,
        val qrCodeId: String,
    ) : QrCodeDto()

    data class QrCodeAppuserResponseDto(
        val firstname: String,
        val lastname: String,
        val id: UUID,
        val qrCodeId: String,
    ) : QrCodeDto()
}
