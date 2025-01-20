package de.lambda9.ready2race.backend.app.race.boundary

import de.lambda9.ready2race.backend.app.auth.entity.Privilege
import de.lambda9.ready2race.backend.app.race.entity.RaceRequest
import de.lambda9.ready2race.backend.database.generated.tables.references.PRIVILEGE
import de.lambda9.ready2race.backend.plugins.authenticate
import de.lambda9.ready2race.backend.plugins.pathParam
import de.lambda9.ready2race.backend.plugins.respondKIO
import de.lambda9.tailwind.core.KIO
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

fun Route.race() {
    route("/race"){

        post {
            val params = call.receive<RaceRequest>()
            call.respondKIO {
                KIO.comprehension {
                    val (user, _) = !authenticate(Privilege.EVENT_EDIT)
                    val eventId = !pathParam("eventId"){ UUID.fromString(it)}
                    RaceService.addRace(params, user.id!!, eventId)
                }
            }
        }

        get {

        }

        route("/{raceId}") {

            get{

            }

            put{

            }

            delete{

            }

        }

    }
}