package de.lambda9.ready2race.backend.app.qrCodeApp.boundary

import de.lambda9.ready2race.backend.app.auth.boundary.AuthService
import de.lambda9.ready2race.backend.app.auth.entity.LoginRequest
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.CheckInOutRequest
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeUpdateDto
import de.lambda9.ready2race.backend.app.teamTracking.boundary.TeamTrackingService
import de.lambda9.ready2race.backend.calls.requests.authenticate
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
                val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_QR_MANAGEMENT)
                val payload = !receiveKIO(QrCodeUpdateDto.QrCodeAppuserUpdate.example)
                QrCodeAppService.updateQrCode(payload, user, scope)
            }
        }

        put("/participant") {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_QR_MANAGEMENT)
                val payload = !receiveKIO(QrCodeUpdateDto.QrCodeParticipantUpdate.example)
                QrCodeAppService.updateQrCode(payload, user, scope)
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
                    val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_QR_MANAGEMENT)
                    val qrCodeId = !pathParam("qrCodeId")
                    QrCodeAppService.deleteQrCode(qrCodeId)
                }
            }
        }

        // Team tracking endpoints
        route("/team/{teamId}") {
            post("/check-in") {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_COMPETITION_CHECK)
                    val teamId = !pathParam("teamId", uuid)
                    val body = !receiveKIO<CheckInOutRequest>(CheckInOutRequest.example)
                    TeamTrackingService.handleTeamCheckIn(teamId, body.eventId, user.id)
                }
            }

            post("/check-out") {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_COMPETITION_CHECK)
                    val teamId = !pathParam("teamId", uuid)
                    val body = !receiveKIO<CheckInOutRequest>(CheckInOutRequest.example)
                    TeamTrackingService.handleTeamCheckOut(teamId, body.eventId, user.id)
                }
            }
        }

        route("/participant/{qrCode}/teams") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_COMPETITION_CHECK)
                    val qrCode = !pathParam("qrCode")
                    val eventId = !queryParam("eventId", uuid)
                    TeamTrackingService.getTeamsByParticipantQrCode(qrCode, eventId)
                }
            }
        }
    }

}

