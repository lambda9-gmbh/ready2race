package de.lambda9.ready2race.backend.app.task.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.task.entity.TaskUpsertDto
import de.lambda9.ready2race.backend.app.task.entity.TaskWithResponsibleUsersSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.task() {
    route("/task") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<TaskWithResponsibleUsersSort>()
                val eventId = !pathParam("eventId", uuid)
                TaskService.page(params, eventId)
            }
        }

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(TaskUpsertDto.example)
                TaskService.addTask(body, eventId, user.id!!)
            }
        }

        route("/{taskId}") {
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val taskId = !pathParam("taskId", uuid)
                    val eventId = !pathParam("eventId", uuid)
                    val body = !receiveKIO(TaskUpsertDto.example)
                    TaskService.updateTask(taskId, body, user.id!!, eventId)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val taskId = !pathParam("taskId", uuid)
                    val eventId = !pathParam("eventId", uuid)
                    TaskService.deleteTask(taskId, eventId)
                }
            }
        }

    }
}