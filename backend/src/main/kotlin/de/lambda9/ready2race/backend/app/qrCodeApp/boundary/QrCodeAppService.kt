package de.lambda9.ready2race.backend.app.qrCodeApp.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.control.toRecord
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeError
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeUpdateDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie

object QrCodeAppService {

    fun loadQrCode(
        qrCodeId: String
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        val userOrParticipant = !QrCodeRepo.getUserOrParticipantByQrCodeIdWithDetails(qrCodeId).orDie()
            //.onNullFail { QrCodeError.QrCodeNotFound }

        when {
            userOrParticipant != null -> KIO.ok(ApiResponse.Dto(userOrParticipant))
            else -> KIO.ok(ApiResponse.NoData)
        }
    }

    private fun isQrCodeInUse(qrCodeId: String): App<ServiceError, Unit> = KIO.comprehension {
        val userOrParticipant = !QrCodeRepo.getUserOrParticipantByQrCodeId(qrCodeId).orDie()
        !KIO.failOn(userOrParticipant != null) { QrCodeError.QrCodeAlreadyInUse }
        KIO.unit
    }

    fun updateQrCode(
        update: QrCodeUpdateDto,
        user: AppUserWithPrivilegesRecord,
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        val record = update.toRecord(user.id!!)
        !isQrCodeInUse(record.qrCodeId)

        QrCodeRepo
            .create(record)
            .orDie()
            .map { ApiResponse.NoData }
    }

    fun deleteQrCode(
        qrCodeId: String,
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        !QrCodeRepo.delete(qrCodeId).orDie()

        ApiResponse.noData
    }

}