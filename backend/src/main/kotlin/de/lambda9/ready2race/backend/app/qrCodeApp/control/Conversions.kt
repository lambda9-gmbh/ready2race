package de.lambda9.ready2race.backend.app.qrCodeApp.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.GroupedParticipantQrAssignmentDto
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.ParticipantQrAssignmentDto
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeDto
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeUpdateDto
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithRolesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantQrAssignmentViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.ParticipantViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.QrCodesRecord
import de.lambda9.tailwind.core.KIO
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

fun ParticipantQrAssignmentViewRecord.toGroupedParticipantQrAssignmentDto(participants: List<ParticipantQrAssignmentDto>): App<Nothing, GroupedParticipantQrAssignmentDto> =
    KIO.ok(
        GroupedParticipantQrAssignmentDto(
            competitionRegistrationId = competitionRegistrationId!!,
            competitionRegistrationName = competitionRegistrationName,
            competitionName = competitionName!!,
            participants = participants
        )
    )

fun ParticipantQrAssignmentViewRecord.extendToParticipantForExecutionDto(): App<Nothing, ParticipantForExecutionDto> =
    KIO.ok(
        ParticipantForExecutionDto(
            id = participantId!!,
            namedParticipantId = namedParticipantId!!,
            namedParticipantName = namedParticipantName!!,
            firstName = firstname!!,
            lastName = lastname!!,
            year = 0,
            gender = Gender.D,
            clubId = clubId!!,
            clubName = "",
            competitionRegistrationId = competitionRegistrationId!!,
            competitionRegistrationName = competitionRegistrationName,
            external = false,
            externalClubName = ""
        )
    )

fun ParticipantForExecutionDto.toParticipantQrAssignmentDto(qrCode: String?): App<Nothing, ParticipantQrAssignmentDto> =
    KIO.ok(
        ParticipantQrAssignmentDto(
            participantId = id,
            firstname = firstName,
            lastname = lastName,
            qrCodeValue = qrCode,
            namedParticipantName = namedParticipantName,
        )
    )