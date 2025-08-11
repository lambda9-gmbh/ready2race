package de.lambda9.ready2race.backend.app.participantTracking.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantTrackingSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.participantTracking() {
    route("/participantTracking"){
        get {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val eventId = !pathParam("eventId", uuid)
                val params = !pagination<ParticipantTrackingSort>()
                ParticipantTrackingService.page(eventId, params, user, scope)
            }
        }
    }
}