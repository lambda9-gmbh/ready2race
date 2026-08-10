package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/** Die RaceClocker-Rennen einer Veranstaltung — unterhalb der Event-Route zu mounten. */
fun Route.raceClockerRace() {
    route("/raceclocker-race") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                RaceClockerRaceService.getRaces(eventId)
            }
        }
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)

                val body = !receiveKIO(RaceClockerRaceRequest.example)
                RaceClockerRaceService.addRace(eventId, user.id!!, body)
            }
        }
        route("/{raceId}") {
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val raceId = !pathParam("raceId", uuid)

                    val body = !receiveKIO(RaceClockerRaceRequest.example)
                    RaceClockerRaceService.updateRace(eventId, raceId, user.id!!, body)
                }
            }
            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val raceId = !pathParam("raceId", uuid)

                    RaceClockerRaceService.deleteRace(eventId, raceId)
                }
            }
        }
    }
}
