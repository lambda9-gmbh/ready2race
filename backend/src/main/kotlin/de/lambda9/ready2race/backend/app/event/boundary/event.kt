package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.plugins.respondKIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

fun Route.event() {
    route("/event") {
        post {
            call.respondKIO {
                val params = call.receive<EventRequest>()
                EventService.addEvent(params, UUID.randomUUID()) // todo: user-UUID from user authentication
            }
        }
    }
}