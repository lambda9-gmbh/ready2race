package de.lambda9.ready2race.backend.app.eventParticipant.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventParticipant.entity.ResendAccessTokenRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.put

fun Route.eventParticipant() {

    put("/resendAccessToken") {
        call.respondComprehension {
            val (user, scope) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.REGISTRATION)
            val eventId = !pathParam("eventId", uuid)
            val participantId = !pathParam("participantId", uuid)
            val body = !receiveKIO(ResendAccessTokenRequest.example)

            EventParticipantService.resendAccessToken(body, eventId, participantId, user, scope)
        }
    }

}