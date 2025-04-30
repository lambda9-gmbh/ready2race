package de.lambda9.ready2race.backend.app.participant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.participant.entity.ParticipantForEventSort
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.participantForEvent() {

    route("/participant") {

        get {
            call.respondComprehension {
                val (user, scope) =!authenticate(Privilege.Action.READ, Privilege.Resource.PARTICIPANT)
                val eventId = !pathParam("eventId", uuid)
                val params = !pagination<ParticipantForEventSort>()
                ParticipantService.pageForEvent(params, eventId, user, scope)
            }
        }

    }
}