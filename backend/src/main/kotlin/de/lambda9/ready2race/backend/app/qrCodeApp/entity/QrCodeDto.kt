package de.lambda9.ready2race.backend.app.qrCodeApp.entity

import java.util.*

sealed class QrCodeDto() {

    enum class QrCodeDtoType{
        Participant,
        User
    }
    data class QrCodeParticipantResponseDto(
        val firstname: String,
        val lastname: String,
        val id: UUID,
        val qrCodeId: String,
        val type: QrCodeDtoType,
        val clubName: String? = null,
        val competitions: List<String> = emptyList(),
    ) : QrCodeDto()

    data class QrCodeAppuserResponseDto(
        val firstname: String,
        val lastname: String,
        val id: UUID,
        val qrCodeId: String,
        val type: QrCodeDtoType,
        val clubName: String? = null,
    ) : QrCodeDto()
}
