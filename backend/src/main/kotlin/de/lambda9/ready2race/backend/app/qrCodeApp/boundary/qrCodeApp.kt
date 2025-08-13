package de.lambda9.ready2race.backend.app.qrCodeApp.boundary

import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeUpdateDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.queryParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import de.lambda9.ready2race.backend.sessions.UserSession
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.qrCodeApp() {

    route("/app") {
        route("/login") {
            rateLimit(RateLimitName("login")) {
                post {
                    call.respondComprehension {
                        val body = !receiveKIO(LoginRequest.example)
                        AuthService.login(body) { token ->
                            sessions.set(UserSession(token))
                        }
                    }
                }
            }
        }

        put("/appuser") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateAppQrManagementGlobal)

                val payload = !receiveKIO(QrCodeUpdateDto.QrCodeAppuserUpdate.example)
                QrCodeAppService.updateQrCode(payload, user)
            }
        }

        put("/participant") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateAppQrManagementGlobal)

                val payload = !receiveKIO(QrCodeUpdateDto.QrCodeParticipantUpdate.example)
                QrCodeAppService.updateQrCode(payload, user)
            }
        }

        route("/{qrCodeId}") {
            get {
                call.respondComprehension {
                    !authenticate()
                    val qrCodeId = !pathParam("qrCodeId")

                    QrCodeAppService.loadQrCode(qrCodeId)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_QR_MANAGEMENT)
                    val qrCodeId = !pathParam("qrCodeId")

                    QrCodeAppService.deleteQrCode(qrCodeId)
                }
            }
        }



        route("/qr-assignment/participants") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_QR_MANAGEMENT)
                    val eventId = !queryParam("eventId", uuid)
                    val clubId = !optionalQueryParam("clubId", uuid)

                    ParticipantQrAssignmentService.getGroupedParticipants(eventId, clubId)
                }
            }
        }
    }

}

