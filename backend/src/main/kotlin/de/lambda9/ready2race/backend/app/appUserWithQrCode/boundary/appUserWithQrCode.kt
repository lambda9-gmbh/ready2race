package de.lambda9.ready2race.backend.app.appUserWithQrCode.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.appUserWithQrCode.entity.AppUserWithQrCodeSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.appUserWithQrCode() {
    route("/appUserWithQrCode") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadUserGlobal)
                val eventId = !pathParam("eventId", uuid)
                val params = !pagination<AppUserWithQrCodeSort>()
                AppUserWithQrCodeService.getAppUsersWithQrCodeForEvent(eventId, params)
            }
        }

        route("/qrCode/{qrCodeId}") {
            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateUserGlobal)
                    val qrCodeId = !pathParam("qrCodeId")
                    AppUserWithQrCodeService.deleteQrCode(qrCodeId)
                }
            }
        }
    }
}