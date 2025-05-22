package de.lambda9.ready2race.backend.app.contactInformation.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationRequest
import de.lambda9.ready2race.backend.app.contactInformation.entity.ContactInformationSort
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.contactInformation() {

    route("/contact") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val payload = !receiveKIO(ContactInformationRequest.example)
                ContactInformationService.addContact(payload, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<ContactInformationSort>()
                ContactInformationService.page(params)
            }
        }

        route("/{contactId}") {

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("contactId", uuid)
                    val payload = !receiveKIO(ContactInformationRequest.example)
                    ContactInformationService.updateContact(id, payload, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("contactId", uuid)
                    ContactInformationService.deleteContact(id)
                }
            }

        }

    }

}