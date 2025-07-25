package de.lambda9.ready2race.backend.app.appUserWithQrCode.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appUserWithQrCode.control.AppUserWithQrCodeRepo
import de.lambda9.ready2race.backend.app.appUserWithQrCode.control.toAppUserWithQrCodeDto
import de.lambda9.ready2race.backend.app.appUserWithQrCode.entity.AppUserWithQrCodeDto
import de.lambda9.ready2race.backend.app.appUserWithQrCode.entity.AppUserWithQrCodeSort
import de.lambda9.ready2race.backend.app.competition.entity.CompetitionError
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.calls.pagination.PaginationParameters
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import java.util.UUID

object AppUserWithQrCodeService {

    fun getAppUsersWithQrCodeForEvent(
        eventId: UUID,
        params: PaginationParameters<AppUserWithQrCodeSort>
    ): App<EventError, ApiResponse.Page<AppUserWithQrCodeDto, AppUserWithQrCodeSort>> = KIO.Companion.comprehension {
        val total = !AppUserWithQrCodeRepo.count(eventId, params.search).orDie()
        val page = !AppUserWithQrCodeRepo.page(eventId, params).orDie()

        page.traverse { it.toAppUserWithQrCodeDto() }.map {
            ApiResponse.Page(
                data = it,
                pagination = params.toPagination(total)
            )
        }
    }

    fun deleteQrCode(
        qrCodeId: String,
    ): App<Nothing, ApiResponse.NoData> = KIO.Companion.comprehension {
        val deleted = !QrCodeRepo.delete(qrCodeId).orDie()

        if (deleted < 1) {
            KIO.fail(CompetitionError.CompetitionNotFound)
        } else {
            noData
        }
        KIO.Companion.ok(ApiResponse.NoData)
    }
}