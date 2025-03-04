package de.lambda9.ready2race.backend.app.role.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.role.entity.RoleRequest
import de.lambda9.ready2race.backend.app.role.entity.RoleWithPrivilegesSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.role() {
    route("/role") {
        post {
            val payload = call.receiveV(RoleRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val user = !authenticate(Privilege.UpdateUserGlobal)

                    val body = !payload
                    RoleService.addRole(body, user.id!!)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.ReadUserGlobal)
                    val params = !pagination<RoleWithPrivilegesSort>()
                    RoleService.page(params)
                }
            }
        }

        route("/{roleId}") {

            put {
                val payload = call.receiveV(RoleRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val user = !authenticate(Privilege.UpdateUserGlobal)
                        val id = !pathParam("roleId") { UUID.fromString(it) }

                        val body = !payload
                        RoleService.updateRole(id, body, user.id!!)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.DeleteUserGlobal)
                        val id = !pathParam("roleId") { UUID.fromString(it) }
                        RoleService.deleteRole(id)
                    }
                }
            }
        }
    }
}