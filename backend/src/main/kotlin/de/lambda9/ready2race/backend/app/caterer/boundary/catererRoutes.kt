package de.lambda9.ready2race.backend.app.caterer.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.caterer.entity.NewCatererTransactionDTO
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.catererRoutes() {
    route("/caterer") {
        post("/transactions") {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateAppCatererGlobal)
                val transaction = !receiveKIO(NewCatererTransactionDTO.example)
                CatererService.createCateringTransaction(transaction, user.id!!)
            }
        }
    }
}