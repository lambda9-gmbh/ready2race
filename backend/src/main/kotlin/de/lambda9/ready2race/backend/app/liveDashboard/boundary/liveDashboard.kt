package de.lambda9.ready2race.backend.app.liveDashboard.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.liveDashboard() {
    route("/event/{eventId}/liveDashboard") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadLiveDashboardGlobal)
                val eventId = !pathParam("eventId", uuid)

                LiveDashboardService.getLiveDashboard(eventId)
            }
        }
    }
}
