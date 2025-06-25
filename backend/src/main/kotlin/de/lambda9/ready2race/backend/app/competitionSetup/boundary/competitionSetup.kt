package de.lambda9.ready2race.backend.app.competitionSetup.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*
import java.util.*

fun Route.competitionSetup(pathParamKey: String) {
    route("/competitionSetup") {
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val key = !pathParam(pathParamKey) { UUID.fromString(it) }

                val body = !receiveKIO(CompetitionSetupDto.example)
                CompetitionSetupService.updateCompetitionSetup(body, user.id!!, key)

            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val key = !pathParam(pathParamKey) { UUID.fromString(it) }

                CompetitionSetupService.getCompetitionSetup(key)
            }
        }
    }
}