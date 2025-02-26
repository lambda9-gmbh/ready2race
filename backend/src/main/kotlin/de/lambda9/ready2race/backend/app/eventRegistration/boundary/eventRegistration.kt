package de.lambda9.ready2race.backend.app.eventRegistration.boundary

import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.eventRegistration() {

    route("/registrationTemplate") {

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate()
                    val eventId = !pathParam("eventId") { UUID.fromString(it) }
                    EventRegistrationService.getEventRegistrationTemplate(eventId)
                }
            }
        }

    }
}