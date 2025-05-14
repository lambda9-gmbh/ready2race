package de.lambda9.ready2race.backend.app.bankAccount.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.bankAccount.entity.AssignBankAccountRequest
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountRequest
import de.lambda9.ready2race.backend.app.bankAccount.entity.BankAccountSort
import de.lambda9.ready2race.backend.calls.requests.ParamParser.Companion.uuid
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.optionalQueryParam
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

fun Route.bankAccount() {

    route("/assignedBankAccount") {
        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val event = !optionalQueryParam("event", uuid)
                BankAccountService.getAssigned(event)
            }
        }
        put {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val payload = !receiveKIO(AssignBankAccountRequest.example)
                BankAccountService.assignAccount(payload, user.id!!)
            }
        }
    }

    route("/bankAccount") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)
                val payload = !receiveKIO(BankAccountRequest.example)
                BankAccountService.addAccount(payload, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<BankAccountSort>()
                BankAccountService.page(params)
            }
        }

        route("/{bankAccountId}") {

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("bankAccountId", uuid)
                    val payload = !receiveKIO(BankAccountRequest.example)
                    BankAccountService.updateAccount(id, payload, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("bankAccountId", uuid)
                    BankAccountService.deleteAccount(id)
                }
            }
        }
    }
}