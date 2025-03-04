package de.lambda9.ready2race.backend.app.fee.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.fee.entity.FeeRequest
import de.lambda9.ready2race.backend.app.fee.entity.FeeSort
import de.lambda9.ready2race.backend.requests.authenticate
import de.lambda9.ready2race.backend.requests.pagination
import de.lambda9.ready2race.backend.requests.pathParam
import de.lambda9.ready2race.backend.requests.receiveV
import de.lambda9.ready2race.backend.responses.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.routing.*
import java.util.*

fun Route.fee() {
    route("/fee") {
        post {
            val payload = call.receiveV(FeeRequest.example)
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.Action.CREATE, Privilege.Resource.EVENT)
                    FeeService.addFee(!payload, user.id!!)
                }
            }
        }

        get {
            call.respondKIO {
                KIO.comprehension {
                    !authenticate(Privilege.Action.READ, Privilege.Resource.EVENT)
                    val params = !pagination<FeeSort>()
                    FeeService.page(params)
                }
            }
        }

        route("/{feeId}"){
            put{
                val payload = call.receiveV(FeeRequest.example)
                call.respondKIO {
                    KIO.comprehension {
                        val (user, _) = !authenticate(Privilege.Action.UPDATE, Privilege.Resource.EVENT)
                        val feeId = !pathParam("feeId") { UUID.fromString(it) }
                        FeeService.updateFee(feeId, !payload, user.id!!)
                    }
                }
            }

            delete{
                call.respondKIO {
                    KIO.comprehension {
                        !authenticate(Privilege.Action.DELETE, Privilege.Resource.EVENT)
                        val feeId = !pathParam("feeId") { UUID.fromString(it) }
                        FeeService.deleteFee(feeId)
                    }
                }
            }
        }
    }
}