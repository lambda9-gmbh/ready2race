package de.lambda9.ready2race.backend.app.eventSchedule.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventSchedule.entity.UpsertScheduleSlotRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.authenticateAny
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
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
        post("/slot") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(UpsertScheduleSlotRequest.example)

                EventScheduleService.createSlot(eventId, body, user.id!!)
            }
        }
        put("/slot/{slotId}") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)
                val body = !receiveKIO(UpsertScheduleSlotRequest.example)

                EventScheduleService.updateSlot(eventId, slotId, body, user.id!!)
            }
        }
        delete("/slot/{slotId}") {
            call.respondComprehension {
                !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)

                EventScheduleService.deleteSlot(eventId, slotId)
            }
        }
        put("/slot/{slotId}/skip") {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.UpdateEventGlobal, Privilege.UpdateLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)

                EventScheduleService.setSlotSkipped(eventId, slotId, skipped = true, userId = user.id!!)
            }
        }
        put("/slot/{slotId}/unskip") {
            call.respondComprehension {
                val user = !authenticateAny(Privilege.UpdateEventGlobal, Privilege.UpdateLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)
                val slotId = !pathParam("slotId", uuid)

                EventScheduleService.setSlotSkipped(eventId, slotId, skipped = false, userId = user.id!!)
            }
        }
    }
}
