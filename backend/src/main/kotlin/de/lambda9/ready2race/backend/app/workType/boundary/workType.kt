package de.lambda9.ready2race.backend.app.workType.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeSort
import de.lambda9.ready2race.backend.app.workType.entity.WorkTypeUpsertDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.workType() {
    route("/worktype") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<WorkTypeSort>()
                WorkTypeService.page(params)
            }
        }

        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                val body = !receiveKIO(WorkTypeUpsertDto.example)
                WorkTypeService.addWorkType(body, user.id!!)
            }
        }

        route("/{workTypeId}") {
            put {
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val workTypeId = !pathParam("workTypeId", uuid)
                    val body = !receiveKIO(WorkTypeUpsertDto.example)
                    WorkTypeService.updateWorkType(workTypeId, body, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val workTypeId = !pathParam("workTypeId", uuid)
                    WorkTypeService.deleteWorkType(workTypeId)
                }
            }
        }

    }
}