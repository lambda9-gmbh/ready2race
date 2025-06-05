package de.lambda9.ready2race.backend.app.role.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.role.entity.RoleRequest
import de.lambda9.ready2race.backend.app.role.entity.RoleWithPrivilegesSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.role() {
    route("/role") {
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateUserGlobal)

                val body = !receiveKIO(RoleRequest.example)
                RoleService.addRole(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate()
                val params = !pagination<RoleWithPrivilegesSort>()
                RoleService.page(params)
            }
        }

        route("/{roleId}") {

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateUserGlobal)
                    val id = !pathParam("roleId", uuid)

                    val body = !receiveKIO(RoleRequest.example)
                    RoleService.updateRole(id, body, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateUserGlobal)
                    val id = !pathParam("roleId", uuid)
                    RoleService.deleteRole(id)
                }
            }
        }
    }
}