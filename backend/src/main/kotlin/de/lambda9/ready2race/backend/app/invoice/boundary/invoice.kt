package de.lambda9.ready2race.backend.app.invoice.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceForEventRegistrationSort
import de.lambda9.ready2race.backend.app.invoice.entity.InvoiceUpdateRequestDto
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.invoice() {

    route("/invoice") {

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadInvoiceGlobal)
                val params = !pagination<InvoiceForEventRegistrationSort>()
                InvoiceService.page(params)
            }
        }

        route("/{invoiceId}") {
            get {
                call.respondComprehension {
                    val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.INVOICE)
                    val id = !pathParam("invoiceId", uuid)
                    InvoiceService.getDownload(id, user, scope)
                }
            }

            put {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateInvoiceGlobal)
                    val id = !pathParam("invoiceId", uuid)
                    val payload = !receiveKIO(InvoiceUpdateRequestDto.example)
                    InvoiceService.setPaid(id, payload.paid)
                }
            }
        }

    }
}