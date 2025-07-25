package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventSort
import de.lambda9.ready2race.backend.app.teamTracking.boundary.TeamTrackingService
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.participantForEvent() {

    route("/participant") {

        get {
            call.respondComprehension {
                val (user, scope) =!authenticate(Privilege.Action.READ, Privilege.Resource.REGISTRATION)
                val eventId = !pathParam("eventId", uuid)
                val params = !pagination<ParticipantForEventSort>()
                ParticipantService.pageForEvent(params, eventId, user, scope)
            }
        }

        route("/{qrCode}/teams") {
            get {
                call.respondComprehension {
                    !authenticate(Privilege.Action.UPDATE, Privilege.Resource.APP_COMPETITION_CHECK)
                    val eventId = !pathParam("eventId", uuid)
                    val qrCode = !pathParam("qrCode")

                    TeamTrackingService.getTeamsByParticipantQrCode(qrCode, eventId)
                }
            }
        }

    }
}