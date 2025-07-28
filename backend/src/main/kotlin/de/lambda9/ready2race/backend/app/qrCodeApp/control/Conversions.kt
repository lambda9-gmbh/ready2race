package de.lambda9.ready2race.backend.app.qrCodeApp.control

import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeDto
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeUpdateDto
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.QrCodesRecord
import java.time.LocalDateTime
import java.util.*

fun ParticipantViewRecord.toQrCodeDto(qrCodeId: String): QrCodeDto.QrCodeParticipantResponseDto =
    QrCodeDto.QrCodeParticipantResponseDto(
        firstname = firstname!!,
        lastname = lastname!!,
        id = id!!,
        type = QrCodeDto.QrCodeDtoType.Participant,
        qrCodeId = qrCodeId
    )

fun ParticipantViewRecord.toQrCodeDtoWithDetails(
    qrCodeId: String,
    clubName: String?,
    competitions: List<String>
): QrCodeDto.QrCodeParticipantResponseDto =
    QrCodeDto.QrCodeParticipantResponseDto(
        firstname = firstname!!,
        lastname = lastname!!,
        id = id!!,
        type = QrCodeDto.QrCodeDtoType.Participant,
        qrCodeId = qrCodeId,
        clubName = clubName,
        competitions = competitions
    )


fun AppUserWithRolesRecord.toQrCodeAppuser(qrCodeId: String): QrCodeDto.QrCodeAppuserResponseDto =
    QrCodeDto.QrCodeAppuserResponseDto(
        firstname = firstname!!,
        lastname = lastname!!,
        id = id!!,
        type = QrCodeDto.QrCodeDtoType.User,
        qrCodeId = qrCodeId
    )

fun AppUserWithRolesRecord.toQrCodeAppuserWithClub(
    qrCodeId: String,
    clubName: String?
): QrCodeDto.QrCodeAppuserResponseDto =
    QrCodeDto.QrCodeAppuserResponseDto(
        firstname = firstname!!,
        lastname = lastname!!,
        id = id!!,
        type = QrCodeDto.QrCodeDtoType.User,
        qrCodeId = qrCodeId,
        clubName = clubName
    )

fun QrCodeUpdateDto.toRecord(userId: UUID): QrCodesRecord = when (this) {
    is QrCodeUpdateDto.QrCodeAppuserUpdate -> QrCodesRecord(
        id = UUID.randomUUID(),
        qrCodeId = this.qrCodeId,
        participant = null,
        appUser = this.id,
        event = this.eventId,
        createdAt = LocalDateTime.now(),
        createdBy = userId
    )

    is QrCodeUpdateDto.QrCodeParticipantUpdate -> QrCodesRecord(
        id = UUID.randomUUID(),
        qrCodeId = this.qrCodeId,
        participant = this.id,
        appUser = null,
        event = this.eventId,
        createdAt = LocalDateTime.now(),
        createdBy = userId
    )
}