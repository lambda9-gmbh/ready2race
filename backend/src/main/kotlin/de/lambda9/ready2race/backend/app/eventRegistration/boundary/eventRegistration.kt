package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.app.eventRegistration.entity.EventRegistrationUpsertDto
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.eventRegistration() {

    route("/registrationTemplate") {

        get {
            call.respondKIO {
                KIO.comprehension {
                    val user = !authenticate()
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    EventRegistrationService.getEventRegistrationTemplate(eventId, user.club!!)
                }
            }
        }

    }

    route("/register") {
        post {
            val payload = call.receiveV(EventRegistrationUpsertDto.example)
            call.respondKIO {
                KIO.comprehension {
                    val user = !authenticate()
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    EventRegistrationService.upsertRegistrationForEvent(eventId, !payload, user.club!!, user.id!!)
                }
            }
        }
    }

}