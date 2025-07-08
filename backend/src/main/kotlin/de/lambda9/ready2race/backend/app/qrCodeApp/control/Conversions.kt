package de.lambda9.ready2race.backend.app.qrCodeApp.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeDto
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantViewRecord
import de.lambda9.tailwind.core.KIO

fun ParticipantViewRecord.toQrCodeDto(): App<Nothing, QrCodeDto.QrCodeParticipantResponseDto> = KIO.ok(
    QrCodeDto.QrCodeParticipantResponseDto(
        firstname = firstname!!,
        lastname = lastname!!,
        id = id!!,
        qrCodeId = qrCodeId!!,
        type = QrCodeDto.QrCodeDtoType.Participant
    )
)

fun AppUserWithRolesRecord.toQrCodeAppuser(): App<Nothing, QrCodeDto.QrCodeAppuserResponseDto> = KIO.ok(
    QrCodeDto.QrCodeAppuserResponseDto(
        firstname = firstname!!,
        lastname = lastname!!,
        id = id!!,
        qrCodeId = qrCodeId!!,
        type = QrCodeDto.QrCodeDtoType.User
    )
)