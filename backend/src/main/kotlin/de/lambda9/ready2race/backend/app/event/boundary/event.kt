package de.lambda9.ready2race.backend.app.event.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.event.entity.EventRequest
import de.lambda9.ready2race.backend.app.event.entity.EventSort
import de.lambda9.ready2race.backend.http.authenticate
import de.lambda9.ready2race.backend.http.pagination
import de.lambda9.ready2race.backend.plugins.respondKIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

fun Route.event() {
    route("/event") {
        post {
            val params = call.receive<EventRequest>()
            call.respondKIO {
                call.authenticate(Privilege.EVENT_EDIT) { userRecord, _ ->
                    EventService.addEvent(params, userRecord.id!!) // todo: "!!" should be save here, right?
                }
            }
        }

        get {
            call.respondKIO {
                call.authenticate(Privilege.EVENT_VIEW) { _, _ ->
                    val params = !call.pagination<EventSort>()
                    EventService.page(params)
                }
            }
        }

        route("/{id}") {
            get {
                call.respondKIO {
                    call.authenticate(Privilege.EVENT_VIEW) { _,_ ->
                        val id = call.parameters["id"]
                        EventService.getEventById(UUID.fromString(id)) // todo: Is UUID.fromString suitable for the job?
                    }
                }
            }

            put {
                val params = call.receive<EventRequest>()
                call.respondKIO {
                    call.authenticate(Privilege.EVENT_EDIT) { userRecord, _ ->
                        val id = call.parameters["id"]
                        EventService.updateEvent(params, UUID.fromString(id), userRecord.id!!)
                    }
                }
            }

            delete {

            }
        }
    }
}