package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationUpsertDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*
import java.util.*

fun Route.eventRegistration() {

    route("/registrationTemplate") {

        get {
            call.respondComprehension {
                val user = !authenticate()
                val eventId = !pathParam("eventId") { UUID.fromString(it) }
                EventRegistrationService.getEventRegistrationTemplate(eventId, user.club!!)
            }
        }

    }

    route("/register") {
        post {
            call.respondComprehension {
                val payload = receiveKIO(EventRegistrationUpsertDto.example)
                val user = !authenticate()
                val eventId = !pathParam("eventId") { UUID.fromString(it) }
                EventRegistrationService.upsertRegistrationForEvent(eventId, !payload, user.club!!, user.id!!)
            }
        }
    }

}