package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.entity.UpdateEventNoticeRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/**
 * Der veranstaltungsweite Hinweisbanner - unterhalb der Event-Route zu mounten (dasselbe
 * Muster wie [de.lambda9.ready2race.backend.app.timingConfig.boundary.eventTimingConfig]).
 * Gelesen wird der Hinweis nicht hier: er steht im EventDto und eingebettet in den gepollten
 * öffentlichen Antworten.
 */
fun Route.eventNotice() {
    route("/notice") {
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                val body = !receiveKIO(UpdateEventNoticeRequest.example)
                EventService.updateEventNotice(
                    eventId = eventId,
                    userId = user.id!!,
                    request = body,
                )
            }
        }
    }
}
