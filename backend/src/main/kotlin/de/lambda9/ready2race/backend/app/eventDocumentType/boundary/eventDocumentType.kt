package de.lambda9.ready2race.backend.app.eventDocumentType.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeRequest
import de.lambda9.ready2race.backend.app.eventDocumentType.entity.EventDocumentTypeSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.UUID

fun Route.eventDocumentType() {
    route("/eventDocumentType") {

        post {
            val payload = call.receiveV(EventDocumentTypeRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)

                    val body = !payload
                    EventDocumentTypeService.addDocumentType(body, user.id!!)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.ReadEventGlobal)
                    val params = !pagination<EventDocumentTypeSort>()
                    EventDocumentTypeService.page(params)
                }
            }
        }

        route("/{eventDocumentTypeId}") {

            put {
                val payload = call.receiveV(EventDocumentTypeRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val user = !authenticate(Privilege.UpdateEventGlobal)
                        val id = !pathParam("eventDocumentTypeId") { UUID.fromString(it) }

                        val body = !payload
                        EventDocumentTypeService.updateDocumentType(id, body, user.id!!)
                    }
                }
            }

            delete {
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.UpdateEventGlobal)
                        val id = !pathParam("eventDocumentTypeId") { UUID.fromString(it) }
                        EventDocumentTypeService.deleteDocumentType(id)
                    }
                }
            }

        }
    }
}