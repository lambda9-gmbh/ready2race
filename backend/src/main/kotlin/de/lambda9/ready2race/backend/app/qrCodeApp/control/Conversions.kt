package de.lambda9.ready2race.backend.app.qrCodeApp.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeDto
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantViewRecord
import de.lambda9.tailwind.core.KIO

fun ParticipantViewRecord.toQrCodeDto(qrCodeId: String): QrCodeDto.QrCodeParticipantResponseDto =
    QrCodeDto.QrCodeParticipantResponseDto(
        firstname = firstname!!,
        lastname = lastname!!,
        id = id!!,
        type = QrCodeDto.QrCodeDtoType.Participant,
        qrCodeId = qrCodeId
    )


fun AppUserWithRolesRecord.toQrCodeAppuser(qrCodeId: String): QrCodeDto.QrCodeAppuserResponseDto =
    QrCodeDto.QrCodeAppuserResponseDto(
        firstname = firstname!!,
        lastname = lastname!!,
        id = id!!,
        type = QrCodeDto.QrCodeDtoType.User,
        qrCodeId = qrCodeId
    )
