package de.lambda9.ready2race.backend.app.qrCodeApp.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeUpdateDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.QrCodesRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.*

object QrCodeAppService {

    fun pruefeQrCode(
        qrCodeId: String
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        val user = !QrCodeRepo.getUserOrParticipantByQrCodeId(qrCodeId).orDie()

        when {
            user != null -> KIO.ok(ApiResponse.Dto(user))
            else -> KIO.ok(ApiResponse.NoData)
        }
    }

    fun updateQrCode(
        participant: QrCodeUpdateDto.QrCodeParticipantUpdate,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        QrCodeRepo.create(
            QrCodesRecord(
                id = UUID.randomUUID(),
                qrCodeId = participant.qrCodeId,
                participant = participant.id,
                appUser = null,
                event = participant.eventId,
                createdAt = LocalDateTime.now(),
                createdBy = user.id
            )
        ).orDie()
            .map { ApiResponse.NoData }
    }

    fun updateQrCode(
        appUser: QrCodeUpdateDto.QrCodeAppuserUpdate,
        user: AppUserWithPrivilegesRecord,
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        QrCodeRepo.create(
            QrCodesRecord(
                id = UUID.randomUUID(),
                qrCodeId = appUser.qrCodeId,
                participant = null,
                appUser = appUser.id,
                event = appUser.eventId,
                createdAt = LocalDateTime.now(),
                createdBy = user.id
            )
        ).orDie()
            .map { ApiResponse.NoData }
    }

    fun deleteQrCode(
        qrCodeId: String,
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        !QrCodeRepo.delete(qrCodeId).orDie()

        ApiResponse.noData
    }

    /*fun getEntitiesWithoutQrCodeId(): App<ServiceError, ApiResponse> = KIO.comprehension {

    }*/

}