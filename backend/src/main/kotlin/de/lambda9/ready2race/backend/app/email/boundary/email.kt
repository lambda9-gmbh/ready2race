package de.lambda9.ready2race.backend.app.email.boundary

import de.lambda9.ready2race.backend.app.appuser.control.AppUserHasRoleRepo
import de.lambda9.ready2race.backend.app.auth.entity.AuthError
import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.email.entity.SmtpConfigOverrideDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.database.ADMIN_ROLE
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.email() {

    route("/smtp-override") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadSmtpConfigGlobal)
                EmailService.getSMTPConfigOverride()
            }

        }
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateSmtpConfigGlobal)
                val configOverride = !receiveKIO(SmtpConfigOverrideDto.example)
                EmailService.setSMTPConfigOverride(configOverride, user.id!!)
            }
        }

        delete {
            call.respondComprehension {
                !authenticate(Privilege.UpdateSmtpConfigGlobal)
                EmailService.deleteSMTPConfigOverride()
            }
        }
    }
}