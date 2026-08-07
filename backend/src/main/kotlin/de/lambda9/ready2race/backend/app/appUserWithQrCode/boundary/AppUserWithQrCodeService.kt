package de.lambda9.ready2race.backend.app.appUserWithQrCode.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.appUserWithQrCode.control.AppUserWithQrCodeRepo
import de.lambda9.ready2race.backend.app.appUserWithQrCode.control.toAppUserWithQrCodeDto
import de.lambda9.ready2race.backend.app.appUserWithQrCode.entity.AppUserWithQrCodeDto
import de.lambda9.ready2race.backend.app.appUserWithQrCode.entity.AppUserWithQrCodeSort
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeError
import de.lambda9.ready2race.backend.pagination.PaginationParameters
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
    ): App<QrCodeError, ApiResponse.NoData> = KIO.Companion.comprehension {
        val deleted = !QrCodeRepo.delete(qrCodeId).orDie()
        // Ohne das `!` war der Fehlerfall ein No-Op: das KIO-Objekt wurde nur gebaut und
        // verworfen, danach gewann das unbedingte ok() darunter. Ein unbekannter Code
        // meldete so Erfolg. Der Fehlertyp war zudem aus der Competition-Welt kopiert.
        !KIO.failOn(deleted < 1) { QrCodeError.QrCodeNotFound }

        noData
    }
}