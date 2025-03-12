package de.lambda9.ready2race.backend.app.competitionSetup.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.competitionSetup.entity.CompetitionSetupDto
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.competitionSetup(pathParamKey: String) { // todo: ok?
    route("/competitionSetup") {
        put {
            val payload = call.receiveV(CompetitionSetupDto.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val key = !pathParam(pathParamKey) { UUID.fromString(it) }

                    val body = !payload
                    CompetitionSetupService.updateCompetitionSetup(body, user.id!!, key)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    val key = !pathParam(pathParamKey) { UUID.fromString(it) }

                    CompetitionSetupService.getCompetitionSetup(key)
                }
            }
        }
    }
}