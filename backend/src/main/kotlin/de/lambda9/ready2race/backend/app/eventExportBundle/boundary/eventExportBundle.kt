package de.lambda9.ready2race.backend.app.eventExportBundle.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.AddEventExportBundleItemRequest
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleOrderRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

/**
 * Die Export-Mappe der Veranstaltung - dieselben Privilegien wie die Dokumentverwaltung
 * (eventDocument): Lesen mit READ EVENT, Pflegen mit UPDATE EVENT.
 */
fun Route.eventExportBundle() {
    route("/exportBundle") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                EventExportBundleService.getBundle(eventId)
            }
        }

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val eventId = !pathParam("eventId", uuid)
                val body = !receiveKIO(AddEventExportBundleItemRequest.example)
                EventExportBundleService.addDocument(eventId, body, user.id!!)
            }
        }

        // Vor "/{itemId}", damit die Reihenfolge nicht als Kennung gelesen wird.
        route("/order") {
            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val body = !receiveKIO(EventExportBundleOrderRequest.example)
                    EventExportBundleService.reorder(eventId, body, user.id!!)
                }
            }
        }

        route("/{itemId}") {
            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val eventId = !pathParam("eventId", uuid)
                    val itemId = !pathParam("itemId", uuid)
                    EventExportBundleService.removeItem(eventId, itemId)
                }
            }
        }
    }
}
