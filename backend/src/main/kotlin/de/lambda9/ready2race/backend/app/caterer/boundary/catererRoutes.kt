package de.lambda9.ready2race.backend.app.caterer.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.caterer.entity.CatererTransactionRequest
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*

fun Route.catererRoutes() {
    route("/caterer") {
        post("/transactions") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateAppCatererGlobal)
                val transaction = !receiveKIO(CatererTransactionRequest.example)
                CatererService.createCateringTransaction(transaction, user.id!!)
            }
        }
    }
}