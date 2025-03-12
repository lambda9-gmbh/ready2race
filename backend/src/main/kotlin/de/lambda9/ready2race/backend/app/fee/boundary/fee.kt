package de.lambda9.ready2race.backend.app.fee.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.fee.entity.FeeRequest
import de.lambda9.ready2race.backend.app.fee.entity.FeeSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import io.ktor.server.routing.*
import java.util.*

fun Route.fee() {
    route("/fee") {
        post {
            call.respondComprehension {
                val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)

                val body = !receiveKIO(FeeRequest.example)
                FeeService.addFee(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                val params = !pagination<FeeSort>()
                FeeService.page(params)
            }
        }

        route("/{feeId}"){
            put{
                call.respondComprehension {
                    val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                    val feeId = !pathParam("feeId") { UUID.fromString(it) }

                    val body = !receiveKIO(FeeRequest.example)
                    FeeService.updateFee(feeId, body, user.id!!)
                }
            }

            delete{
                call.respondComprehension {
                    !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                    val feeId = !pathParam("feeId") { UUID.fromString(it) }
                    FeeService.deleteFee(feeId)
                }
            }
        }
    }
}