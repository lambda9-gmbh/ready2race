package de.lambda9.ready2race.backend.app.user.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.user.entity.AppUserWithRolesSort
import de.lambda9.ready2race.backend.http.authenticate
import de.lambda9.ready2race.backend.http.pagination
import de.lambda9.ready2race.backend.plugins.respondKIO
import io.ktor.server.routing.*

fun Route.user() {
    route("/user") {
        get {
            call.respondKIO {
                call.authenticate(Privilege.USER_VIEW) { _, _ ->
                    val params = !call.pagination<AppUserWithRolesSort>()
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