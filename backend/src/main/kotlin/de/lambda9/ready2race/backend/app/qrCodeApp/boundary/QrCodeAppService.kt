package de.lambda9.ready2race.backend.app.qrCodeApp.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.appuser.control.AppUserRepo
import de.lambda9.ready2race.backend.app.appuser.entity.AppUserError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.control.ParticipantRepo
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantError
import de.lambda9.ready2race.backend.app.qrCodeApp.control.toQrCodeAppuser
import de.lambda9.ready2race.backend.app.qrCodeApp.control.toQrCodeDto
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeUpdateDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.generated.tables.records.AppUserWithPrivilegesRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse

object QrCodeAppService {

    fun pruefeQrCode(
        qrCodeId: String
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        val participant = !ParticipantRepo.getParticipantByQrCodeId(qrCodeId).orDie()
        val appUser = !AppUserRepo.getOneByQrCodeId(qrCodeId).orDie()

        when {
            participant.isNotEmpty() -> participant.traverse { it.toQrCodeDto() }.map { ApiResponse.ListDto(it) }
            appUser != null -> appUser.toQrCodeAppuser().map { ApiResponse.Dto(it)}
            else -> KIO.ok(ApiResponse.NoData)
        }
    }

    fun updateQrCode(
        participant: QrCodeUpdateDto.QrCodeParticipantUpdate,
        user: AppUserWithPrivilegesRecord,
        scope: Privilege.Scope
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        ParticipantRepo.update(participant.id, null, user, scope) {
            qrCodeId = participant.qrCodeId
        }.orDie()
            .onNullFail { ParticipantError.ParticipantNotFound }
            .map { ApiResponse.NoData }
    }

    fun updateQrCode(
        participant: QrCodeUpdateDto.QrCodeAppuserUpdate,
    ): App<ServiceError, ApiResponse> = KIO.comprehension {
        AppUserRepo.update(participant.id) {
            qrCodeId = participant.qrCodeId
        }.orDie()
            .onNullFail { AppUserError.NotFound }
            .map { ApiResponse.NoData }
    }

    /*fun getEntitiesWithoutQrCodeId(): App<ServiceError, ApiResponse> = KIO.comprehension {

    }*/

}