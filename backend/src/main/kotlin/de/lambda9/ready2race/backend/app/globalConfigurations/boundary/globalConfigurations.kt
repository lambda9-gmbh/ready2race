package de.lambda9.ready2race.backend.app.globalConfigurations.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.globalConfigurations.entity.UpdateGlobalConfigurationsRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.globalConfigurations() {
    route("/globalConfigurations") {
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                val request = !receiveKIO(UpdateGlobalConfigurationsRequest.example)

                GlobalConfigurationsService.updateConfigurations(request, user.id!!)
            }
        }
        get("/createClubOnRegistration") {
            call.respondComprehension {
                GlobalConfigurationsService.getCreateClubOnRegistration()
            }
        }
    }
}