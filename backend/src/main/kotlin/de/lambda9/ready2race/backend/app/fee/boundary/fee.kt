package de.lambda9.ready2race.backend.app.fee.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.fee.entity.FeeRequest
import de.lambda9.ready2race.backend.app.fee.entity.FeeSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.*

fun Route.fee() {
    route("/fee") {
        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(FeeRequest.example)
                FeeService.addFee(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<FeeSort>()
                FeeService.page(params)
            }
        }

        route("/{feeId}"){
            put{
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val feeId = !pathParam("feeId", uuid)

                    val body = !receiveKIO(FeeRequest.example)
                    FeeService.updateFee(feeId, body, user.id!!)
                }
            }

            delete{
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val feeId = !pathParam("feeId", uuid)
                    FeeService.deleteFee(feeId)
                }
            }
        }
    }
}