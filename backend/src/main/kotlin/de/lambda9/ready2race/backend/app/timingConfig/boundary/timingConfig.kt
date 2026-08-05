package de.lambda9.ready2race.backend.app.timingConfig.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingConfigRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

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
