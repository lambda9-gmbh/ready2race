package de.lambda9.ready2race.backend.app.workShift.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftUpsertDto
import de.lambda9.ready2race.backend.app.workShift.entity.WorkShiftWithAssignedUsersSort
import de.lambda9.ready2race.backend.calls.requests.*
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.datetime
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.workShift() {
    route("/workshift") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<WorkShiftWithAssignedUsersSort>()
                val eventId = !pathParam("eventId", uuid)
                val timeFrom = !optionalQueryParam("timeFrom", datetime)
                val timeTo = !optionalQueryParam("timeTo", datetime)
                WorkShiftService.page(params, eventId, timeFrom, timeTo)
            }
        }

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(WorkShiftUpsertDto.example)

                WorkShiftService.addWorkShift(eventId, body, user.id!!)
            }
        }

        route("/{workshiftId}") {
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val workshiftId = !pathParam("workshiftId", uuid)
                    val body = !receiveKIO(WorkShiftUpsertDto.example)

                    WorkShiftService.updateWorkShift(workshiftId, body, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val workTypeId = !pathParam("workTypeId", uuid)

                    WorkShiftService.deleteWorkShift(workTypeId)
                }
            }
        }

    }
}