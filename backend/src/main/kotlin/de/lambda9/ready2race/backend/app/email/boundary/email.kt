package de.lambda9.ready2race.backend.app.email.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.entity.SmtpConfigOverrideDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.email() {

    route("/smtp-override") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadAdministrationConfigGlobal)
                EmailService.getSMTPConfigOverride()
            }

        }
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                val configOverride = !receiveKIO(SmtpConfigOverrideDto.example)
                EmailService.setSMTPConfigOverride(configOverride, user.id!!)
            }
        }

        delete {
            call.respondComprehension {
                !authenticate(Privilege.UpdateAdministrationConfigGlobal)
                EmailService.deleteSMTPConfigOverride()
            }
        }
    }
}