package de.lambda9.ready2race.backend.app.invoice.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.invoice() {

    route("/invoice") {

        get("/{invoiceId}") {
            call.respondComprehension {
                val (user, scope) = !authenticate(Privilege.Action.READ, Privilege.Resource.INVOICE)
                val id = !pathParam("invoiceId", uuid)
                InvoiceService.getDownload(id, user, scope)
            }
        }

    }
}