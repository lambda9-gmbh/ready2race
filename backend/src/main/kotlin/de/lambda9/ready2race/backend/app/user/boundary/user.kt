package de.lambda9.ready2race.backend.app.user.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.user.entity.AppUserWithRolesSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*

fun Route.user() {
    route("/user") {
        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.USER)
                    val params = !pagination<AppUserWithRolesSort>()
                    AppUserService.page(params)
                }
            }
        }

        post {

        }

        put("/{userId}") {

        }
    }
}