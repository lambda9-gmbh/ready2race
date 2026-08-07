package de.lambda9.ready2race.backend.app.timingConfig.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.timingConfig.entity.EventTimingConfigRequest
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingConfigRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/** Die Zeitnahme-Voreinstellung der Veranstaltung - unterhalb der Event-Route zu mounten. */
fun Route.eventTimingConfig() {
    route("/timing-config") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                TimingConfigService.getEventTimingConfig(eventId)
            }
        }
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                val body = !receiveKIO(EventTimingConfigRequest.example)
                TimingConfigService.updateEventTimingConfig(
                    eventId = eventId,
                    userId = user.id!!,
                    request = body,
                )
            }
        }
    }
}

fun Route.timingConfig() {
    route("/timing-config") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                TimingConfigService.getTimingConfig(competitionId)
            }
        }
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val competitionId = !pathParam("competitionId", uuid)

                val body = !receiveKIO(TimingConfigRequest.example)
                TimingConfigService.updateTimingConfig(
                    competitionId = competitionId,
                    userId = user.id!!,
                    request = body,
                )
            }
        }
    }
}
