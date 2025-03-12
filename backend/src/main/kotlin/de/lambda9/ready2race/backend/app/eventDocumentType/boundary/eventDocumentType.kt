package de.lambda9.ready2race.backend.app.eventDocumentType.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeRequest
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.UUID

fun Route.eventDocumentType() {
    route("/eventDocumentType") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(EventDocumentTypeRequest.example)
                EventDocumentTypeService.addDocumentType(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<EventDocumentTypeSort>()
                EventDocumentTypeService.page(params)
            }
        }

        route("/{eventDocumentTypeId}") {

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("eventDocumentTypeId") { UUID.fromString(it) }

                    val body = !receiveKIO(EventDocumentTypeRequest.example)
                    EventDocumentTypeService.updateDocumentType(id, body, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("eventDocumentTypeId") { UUID.fromString(it) }
                    EventDocumentTypeService.deleteDocumentType(id)
                }
            }

        }
    }
}