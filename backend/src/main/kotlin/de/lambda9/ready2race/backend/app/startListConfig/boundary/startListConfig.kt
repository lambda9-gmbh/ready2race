package de.lambda9.ready2race.backend.app.startListConfig.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigRequest
import de.lambda9.ready2race.backend.app.startListConfig.entity.StartListConfigSort
import de.lambda9.ready2race.backend.calls.requests.authenticate
import de.lambda9.ready2race.backend.calls.requests.pagination
import de.lambda9.ready2race.backend.calls.requests.pathParam
import de.lambda9.ready2race.backend.calls.requests.receiveKIO
import de.lambda9.ready2race.backend.calls.responses.respondComprehension
import de.lambda9.ready2race.backend.parsing.Parser.Companion.uuid
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.startListConfig() {
    route("/startListConfig") {

        post {
            call.respondComprehension {
                val user = !authenticate(Privilege.UpdateEventGlobal)

                val body = !receiveKIO(StartListConfigRequest.example)
                StartListConfigService.addConfig(body, user.id!!)
            }
        }

        get {
            call.respondComprehension {
                !authenticate(Privilege.ReadEventGlobal)
                val params = !pagination<StartListConfigSort>()
                StartListConfigService.page(params)
            }
        }

        route("/{startListConfigId}") {

            put {
                call.respondComprehension {
                    val user = !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("startListConfigId", uuid)

                    val body = !receiveKIO(StartListConfigRequest.example)
                    StartListConfigService.updateConfig(id, body, user.id!!)
                }
            }

            delete {
                call.respondComprehension {
                    !authenticate(Privilege.UpdateEventGlobal)
                    val id = !pathParam("startListConfigId", uuid)
                    StartListConfigService.deleteConfig(id)
                }
            }
        }
    }
}