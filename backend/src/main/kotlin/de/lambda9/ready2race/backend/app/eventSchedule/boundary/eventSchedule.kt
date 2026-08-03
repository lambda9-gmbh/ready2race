package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.eventSchedule() {
    route("/event/{eventId}/schedule") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                EventScheduleService.getSchedule(eventId)
            }
        }
    }
}
